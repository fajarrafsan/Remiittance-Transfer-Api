package com.finshot.remittance.service;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.exception.ApiException;
import com.finshot.remittance.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Component
public class TransferRequestValidator {
    static final String SEND_CURRENCY = "KRW";
    static final String RECEIVE_CURRENCY = "PHP";
    static final BigDecimal MIN_SEND_AMOUNT = new BigDecimal("10000");
    static final int MAX_SEND_AMOUNT_DIGITS = 15;
    static final int MAX_RECIPIENT_NAME_LENGTH = 100;
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final TransferRepository repository;

    public TransferRequestValidator(TransferRepository repository) {
        this.repository = repository;
    }


    public String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.validation("Idempotency-key header is required");
        }

        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ApiException.validation("Idempotency-key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        return key;
    }

    public BigDecimal validate(CreateTransferRequest request) {

        if (request == null) {
            throw ApiException.validation("request body is required");
        }

        BigDecimal sendAmount = parseSendAmount(request.sendAmount());
        if (sendAmount.compareTo(MIN_SEND_AMOUNT) < 0) {
            throw ApiException.validation("sendAmount must be at least 10000");
        }

        String recipientName = request.recipientName();
        if (recipientName == null || recipientName.isBlank()) {
            throw ApiException.validation("recipientName must not be empty");
        }

        if (recipientName.length() > MAX_RECIPIENT_NAME_LENGTH) {
            throw ApiException.validation("recipientName must be at most " + MAX_RECIPIENT_NAME_LENGTH + " characters");
        }

        if (!SEND_CURRENCY.equals(request.sendCurrency())) {
            throw ApiException.validation("sendCurrency must be KRW");
        }

        if (!RECEIVE_CURRENCY.equals(request.receiveCurrency())) {
            throw ApiException.validation("receiveCurrency must be PHP");
        }

        String customerId = request.customerId();
        if (customerId == null || customerId.isBlank()) {
            throw ApiException.validation("customerId must not be empty");
        }

        if (!repository.customerExists(customerId)) {
            throw ApiException.validation("unknown customerId " + customerId);
        }

        return sendAmount;
    }

    private BigDecimal parseSendAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.validation("send amount is required");
        }

        String value = raw.trim();
        if (!NUMBER.matcher(value).matches()) {
            throw ApiException.validation("sendAmount must be a number");
        }

        BigDecimal amount = new BigDecimal(value);
        if (amount.scale() > 0) {
            throw ApiException.validation("sendAmount must be a whole number (no decimals)");
        }

        if (amount.signum() <= 0) {
            throw ApiException.validation("sendAmount must be greater than 0");
        }

        if (amount.precision() > MAX_SEND_AMOUNT_DIGITS) {
            throw ApiException.validation("sendAmount is too large");
        }

        return amount;

    }

}
