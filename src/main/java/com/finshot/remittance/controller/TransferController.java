package com.finshot.remittance.controller;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    static final String IDEMPOTENCY_KEY = "Idempotency-key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CreateTransferRequest request
    ) {
        TransferService.CreateResult result = transferService.create(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(TransferResponse.from(result.transfer()));

    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable("transferId") String transferId) {
        return TransferResponse.from(transferService.)
    }
}
