package com.finshot.remittance.service;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DailyLimitTest {

    @Autowired
    TransferService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    public void parallelRequestsCannotTogetherExceedTheLimit() throws Exception {
        service.create("limit-base", request("2000000"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        for (String key : List.of("limit-a", "limit-b")) {
            results.add(pool.submit(() -> {
                start.await();   // both requests at the same moment
                try {
                    service.create(key, request("600000"));
                    return "CREATED";
                } catch (ApiException e) {
                    return e.code().name();
                }
            }));
        }

        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "LIMIT_EXCEEDED");
        assertThat(jdbc.queryForObject(
                "SELECT SUM(send_amount) FROM transfers WHERE customer_id = 'C003'", Long.class))
                .isEqualTo(2_600_000L);
    }

    private static CreateTransferRequest request(String sendAmount) {
        return new CreateTransferRequest("C003", "KRW", sendAmount, "PHP", "Ana Reyes");
    }
}
