package com.finshot.remittance.service;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.exception.ApiException;
import com.finshot.remittance.model.Transfer;
import com.finshot.remittance.model.TransferAmounts;
import com.finshot.remittance.model.TransferStatus;
import com.finshot.remittance.repository.TransferRespository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    static final BigDecimal DAILY_LIMIT = new BigDecimal("3000000");
    static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");

    public record CreateResult(Transfer transfer, boolean replayed) {
    }

    private final TransferRespository respository;
    private final TransferRequestValidator validator;
    private final TransferCalculator calculator;
    private final TransactionTemplate transaction;

    public TransferService(TransferRespository respository, TransferRequestValidator validator, TransferCalculator calculator, TransactionTemplate transaction) {
        this.respository = respository;
        this.validator = validator;
        this.calculator = calculator;
        this.transaction = transaction;
    }


    public CreateResult create(String idempotencyKey, CreateTransferRequest request) {
        String key = validator.validateIdempotencyKey(idempotencyKey);
        BigDecimal sendAmount = validator.validate(request);
        String requestHash = requestHash(request, sendAmount);

        Optional<Transfer> existing = respository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        TransferAmounts amounts = calculator.calculate(sendAmount);
        OffsetDateTime now = now();
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString(), request.customerId(), key, requestHash,
                request.sendCurrency(), amounts.sendAmount(), amounts.fee(), amounts.totalDebit(),
                amounts.fxRate(), request.receiveCurrency(), amounts.receiveAmount(),
                request.recipientName(), TransferStatus.REQUESTED, null, null, now, now);

        try {
            return transaction.execute(status -> insertWithinDailyLimit(transfer));
        } catch (DataIntegrityViolationException e) {
            Transfer winner = respository.findByIdempotencyKey(key).orElseThrow(() -> e);
            return replay(winner, requestHash);
        }
    }

    public Transfer get(String transferId) {
        return respository.findById(transferId).orElseThrow(() -> ApiException.notFound("transfer not found: " + transferId));
    }

    // satu transaksi: kunci baris customer tersebut, lalu periksa batas dan lakukan penyisipan
    private CreateResult insertWithinDailyLimit(Transfer transfer) {
        respository.lockCustomer(transfer.customerId());

        Optional<Transfer> existing = respository.findByIdempotencyKey(transfer.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), transfer.requestHash());
        }

        ZonedDateTime dayStart = transfer.createdAt().atZoneSameInstant(DAY_ZONE)
                .toLocalDate().atStartOfDay(DAY_ZONE);
        BigDecimal usedToday = respository.sumActiveSendAmount(transfer.customerId(), dayStart.toOffsetDateTime(), dayStart.plusDays(1).toOffsetDateTime());
        if (usedToday.add(transfer.sendAmount()).compareTo(DAILY_LIMIT) > 0) {
            throw ApiException.limitExceeded("daily limit of " + DAILY_LIMIT.toPlainString() + " KRW exceeded: " + usedToday.toPlainString() + " KRW already user today (asia/seoul)");
        }

        respository.insert(transfer);
        return new CreateResult(transfer, false);
    }

    private CreateResult replay(Transfer existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw ApiException.conflict("Idempotency-key was already used with different request data");
        }
        return new CreateResult(existing, true);
    }

    // "0500000" dan "500000" akan menghasilkan hasil yang sama "500000"
    static String requestHash(CreateTransferRequest request, BigDecimal sendAmount) {
        String canonical = String.join("\n",
                request.customerId(), request.sendCurrency(), sendAmount.toPlainString(),
                request.receiveCurrency(), request.recipientName());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
