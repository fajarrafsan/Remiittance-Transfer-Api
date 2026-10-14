package com.finshot.remittance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.dto.PartnerCallbackRequest;
import com.finshot.remittance.dto.SendTransferRequest;
import com.finshot.remittance.model.Transfer;
import com.finshot.remittance.model.TransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PartnerFlowTest {

    @Autowired
    TransferService transferService;

    @Autowired
    PartnerService partnerService;

    @Test
    void acceptedTransferWaitsForCallbackAndTheCallbackIsIdempotent() {
        String transferId = create("partner-ok");

        Transfer sent = partnerService.send(transferId, new SendTransferRequest("PR-TEST-OK-0003"));
        assertThat(sent.status()).isEqualTo(TransferStatus.SENDING);

        PartnerCallbackRequest callback = new PartnerCallbackRequest("PR-TEST-OK-0003", "evt-test-1", "COMPLETED");
        PartnerService.CallbackResult first = partnerService.handleCallback(callback);
        PartnerService.CallbackResult second = partnerService.handleCallback(callback);

        assertThat(first.replayed()).isFalse();
        assertThat(first.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void rejectedTransferFailsAfterThreeAttempts() {
        String transferId = create("partner-fail");

        long start = System.currentTimeMillis();
        Transfer result = partnerService.send(transferId, new SendTransferRequest("PR-TEST-FAIL-0001"));

        assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.failureReason()).isNotBlank();
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(3 * 200L);

    }

    private String create(String idempotencyKey) {
        return transferService.create(idempotencyKey,
                        new CreateTransferRequest("C002", "KRW", "100000", "PHP", "Maria Santos"))
                .transfer().transferId();
    }
}
