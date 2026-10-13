package com.finshot.remittance.controller;

import com.finshot.remittance.dto.CreateTransferRequest;
import com.finshot.remittance.dto.SendTransferRequest;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.service.PartnerService;
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
    private final PartnerService partnerService;

    public TransferController(TransferService transferService, PartnerService partnerService) {
        this.transferService = transferService;
        this.partnerService = partnerService;
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
        return TransferResponse.from(transferService.get(transferId));
    }

    @PostMapping("/{transferId}/cancel")
    public TransferResponse cancel(@PathVariable("transferId") String transferId) {
        return TransferResponse.from(transferService.cancel(transferId));
    }

    @PostMapping("/{transferId}/send")
    public TransferResponse send(@PathVariable("transferId") String transferId,
                                 @RequestBody(required = false) SendTransferRequest request) {
        return TransferResponse.from(partnerService.send(transferId, request));
    }
}
