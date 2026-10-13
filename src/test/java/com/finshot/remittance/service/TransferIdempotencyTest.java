package com.finshot.remittance.service;

import com.finshot.remittance.dto.CreateTransferRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TransferIdempotencyTest {

    @Autowired
    TransferService service;

    @Autowired
    JdbcTemplate jdbc;


    @Test
    void retryWithSameKeyReturnsTheOriginalTransfer() {
        TransferService.CreateResult first = service.create("test-key-retri", request("500000"));
        TransferService.CreateResult retry = service.create("test-key-retri", request("500000"));

        Assertions.assertThat(first.replayed()).isFalse();
        Assertions.assertThat(retry.replayed()).isTrue();
        Assertions.assertThat(retry.transfer().transferId()).isEqualTo(first.transfer().transferId());
        Assertions.assertThat(rowsWithKey("test-key-retri")).isEqualTo(1);

    }

    private static CreateTransferRequest request(String sendAmount) {
        return new CreateTransferRequest("C001", "KRW", sendAmount, "PHP", "Juan Dela Cruz");
    }

    private int rowsWithKey(String key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?", Integer.class, key);
    }
}
