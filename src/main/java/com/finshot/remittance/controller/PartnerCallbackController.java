package com.finshot.remittance.controller;

import com.finshot.remittance.dto.PartnerCallbackRequest;
import com.finshot.remittance.dto.TransferResponse;
import com.finshot.remittance.service.PartnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/callbacks")
public class PartnerCallbackController {
    private final PartnerService partnerService;

    public PartnerCallbackController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @PostMapping("/partner")
    public ResponseEntity<TransferResponse> partnerCallback(@RequestBody(required = false) PartnerCallbackRequest request) {
        PartnerService.CallbackResult result = partnerService.handleCallBack(request);
        return ResponseEntity.ok()
                .header(TransferController.IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(TransferResponse.from(result.transfer()));
    }
}
