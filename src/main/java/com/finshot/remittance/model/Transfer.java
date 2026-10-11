package com.finshot.remittance.model;


import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Transfer(
        String transferId,
        String customerId,
        String idempotencyKey,
        String requestHash,
        String sendCurrency,
        BigDecimal sendAmount,
        BigDecimal fee,
        BigDecimal totalDebit,
        BigDecimal fxRate,
        String receiveCurrency,
        BigDecimal receiveAmount,
        String recipientName,
        TransferStatus status,
        String partnerRef,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}