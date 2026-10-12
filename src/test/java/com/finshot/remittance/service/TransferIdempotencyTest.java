package com.finshot.remittance.service;

import com.finshot.remittance.dto.CreateTransferRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class TransferIdempotencyTest {
    @Autowired
    TransferService transferService;

    @Autowired
    JdbcTemplate jdbcTemplate;

private static CreateTransferRequest request;
}
