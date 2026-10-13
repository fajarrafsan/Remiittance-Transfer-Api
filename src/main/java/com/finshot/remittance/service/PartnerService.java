package com.finshot.remittance.service;

import com.finshot.remittance.client.PartnerClient;
import com.finshot.remittance.dto.PartnerCallbackRequest;
import com.finshot.remittance.dto.SendTransferRequest;
import com.finshot.remittance.exception.ApiException;
import com.finshot.remittance.model.Transfer;
import com.finshot.remittance.model.TransferStatus;
import com.finshot.remittance.repository.PartnerEventRepository;
import com.finshot.remittance.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class PartnerService {

    static final int MAX_ATTEMPTS = 3;
    static final long FIRST_BACKOFF_MS = 100;            // then 200 ms
    static final String CALLBACK_STATUS = "COMPLETED";
    static final int MAX_EVENT_ID_LENGTH = 64;

    private static final Pattern PARTNER_REF = Pattern.compile("[A-Za-z0-9_-]{0,63}[0-9]");

    private static final Logger log = LoggerFactory.getLogger(PartnerService.class);

    public record CallbackResult(Transfer transfer, boolean replayed) {
    }

    private final TransferRepository transfers;
    private final PartnerEventRepository events;
    private final PartnerClient partnerClient;
    private final TransactionTemplate transaction;

    public PartnerService(TransferRepository transfers,
                          PartnerEventRepository events,
                          PlatformTransactionManager transactionManager,
                          PartnerClient partnerClient
    ) {
        this.transfers = transfers;
        this.events = events;
        this.partnerClient = partnerClient;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // accepted -> stays SENDING until the callback, 3 failed attempts -> FAILED
    public Transfer send(String transferId, SendTransferRequest request) {
        String chosenRef = request == null ? null : request.partnerRef();
        if (chosenRef != null) {
            validatePartnerRef(chosenRef);
        }
        Transfer transfer = getTransfer(transferId);
        if (!transfer.status().canMoveTo(TransferStatus.SENDING)) {
            throw cannotSend(transfer.status());
        }
        String partnerRef = chosenRef != null ? chosenRef : String.format("PR%08d", transfers.nextPartnerRefNumber());

        // SENDING and the partnerRef are saved and committed BEFORE the partner is called,
        // so a callback that arrives early always finds the transfer.
        boolean started;
        try {
            started = transfers.startSending(transferId, partnerRef, now());
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("partnerRef " + partnerRef + " is already used by another transfer");
        }
        if (!started) {
            // Another request changed the status between the check above and the UPDATE.
            throw cannotSend(getTransfer(transferId).status());
        }

        if (!callPartnerWithRetry(transferId, partnerRef)) {
            // Only changes a transfer that is still SENDING: it never overwrites a COMPLETED callback.
            transfers.markFailed(transferId,
                    "partner did not accept the request after " + MAX_ATTEMPTS + " attempts", now());
        }
        return getTransfer(transferId);
    }

    private static ApiException cannotSend(TransferStatus current) {
        return ApiException.conflict("cannot change status from " + current + " to " + TransferStatus.SENDING);
    }

    private boolean callPartnerWithRetry(String transferId, String partnerRef) {
        long backoffMs = FIRST_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean accepted = partnerClient.send(partnerRef);
            log.info("partner attempt {}/{} partnerRef={} outcome={}",
                    attempt, MAX_ATTEMPTS, partnerRef, accepted ? "ACCEPTED" : "FAILED");
            if (accepted) {
                return true;
            }
            if (attempt < MAX_ATTEMPTS) {
                if (!pause(backoffMs)) {
                    return false;
                }
                backoffMs *= 2;
                if (getTransfer(transferId).status() != TransferStatus.SENDING) {
                    // A callback already completed the transfer, so the partner did accept it. Stop retrying.
                    return true;
                }
            }
        }
        return false;
    }

    public CallbackResult handleCallBack(PartnerCallbackRequest request) {
        validateCallBack(request);
        String eventId = request.eventId();
        String partnerRef = request.partnerRef();
        try {
            return transaction.execute(status -> {
                Optional<String> seen = events.findPartnerRef(eventId);
                if (seen.isPresent()) {
                    return replayEvent(eventId, seen.get(), partnerRef);
                }
                Transfer transfer = transfers.findByPartnerRef(partnerRef)
                        .orElseThrow(() -> ApiException.notFound("unknown partnerRef: " + partnerRef));
                OffsetDateTime now = now();
                events.insert(eventId, partnerRef, CALLBACK_STATUS, now);
                if (!transfers.updateStatus(transfer.transferId(), TransferStatus.SENDING, TransferStatus.COMPLETED, now)) {
                    throw ApiException.conflict("a callback is only valid for a SENDING transfer, this one is "
                            + transfer.status());
                }
                return new CallbackResult(getTransfer(transfer.transferId()), false);
            });
        } catch (DataIntegrityViolationException e) {
            // The same eventId was delivered twice at the same moment and the other delivery was saved first.
            String seenRef = events.findPartnerRef(eventId).orElseThrow(() -> e);
            return replayEvent(eventId, seenRef, partnerRef);
        }
    }

    private CallbackResult replayEvent(String eventId, String seenPartnerRef, String partnerRef) {
        if (!seenPartnerRef.equals(partnerRef)) {
            throw ApiException.conflict("eventId " + eventId + " was already used for partnerRef " + seenPartnerRef);
        }

        Transfer transfer = transfers.findByPartnerRef(partnerRef)
                .orElseThrow(() -> ApiException.notFound("unknown partnerRef: " + partnerRef));
        return new CallbackResult(transfer, true);
    }

    private static void validateCallBack(PartnerCallbackRequest request) {
        if (request == null) {
            throw ApiException.validation("request body is required");
        }

        if (request.partnerRef() == null || request.partnerRef().isBlank()) {
            throw ApiException.validation("partnerRef is required");
        }

        if (request.eventId() == null || request.eventId().isBlank()) {
            throw ApiException.validation("eventId is required");
        }

        if (request.eventId().length() > MAX_EVENT_ID_LENGTH) {
            throw ApiException.validation("eventId must be at most " + MAX_EVENT_ID_LENGTH + " characters");
        }

        if (!CALLBACK_STATUS.equals(request.status())) {
            throw ApiException.validation("status must be COMPLETED");
        }
    }

    private static void validatePartnerRef(String partnerRef) {
        if (!PARTNER_REF.matcher(partnerRef).matches()) {
            throw ApiException.validation(
                    "partnerRef must be 1-64 letters, digits, '-' or '_', and end with a digit");
        }
    }

    private Transfer getTransfer(String transferId) {
        return transfers.findById(transferId)
                .orElseThrow(() -> ApiException.notFound("transfer not found: " + transferId));
    }

    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }


    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
