package com.finshot.remittance.service;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.exception.ApiException;
import com.finshot.remittance.exception.ErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

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

    @Test
    void sameKeyWithDifferentDataIsConflict() {
        service.create("test-key-conflict", request("500000"));
        Assertions.assertThatThrownBy(() -> service.create("test-key-conflict", request("600000")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> Assertions.assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        Assertions.assertThat(rowsWithKey("test-key-conflict")).isEqualTo(1);
    }

    @Test
    void parallelRetriesCreateOnlyOneTransfers() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.create("test-key-parallel", request("500000")).transfer().transferId();
            }));
        }

        start.countDown();

        Set<String> transferIds = new HashSet<>();
        for (Future<String> result : results) {
            transferIds.add(result.get(10, TimeUnit.SECONDS));
        }

        pool.shutdown();
        Assertions.assertThat(transferIds).hasSize(1);
        Assertions.assertThat(rowsWithKey("test-key-parallel")).isEqualTo(1);
    }


    private static CreateTransferRequest request(String sendAmount) {
        return new CreateTransferRequest("C001", "KRW", sendAmount, "PHP", "Juan Dela Cruz");
    }

    private int rowsWithKey(String key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?", Integer.class, key);
    }
}
