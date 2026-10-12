package com.finshot.remittance.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.finshot.remittance.model.Transfer;

import java.time.format.DateTimeFormatter;

@JsonPropertyOrder({
        "transferId", "status", "customerId", "recipientName",
        "sendCurrency", "sendAmount", "fee", "totalDebit",
        "fxRate", "receiveCurrency", "receiveAmount",
        "partnerRef", "failureReason", "createdAt", "updatedAt"})
public record TransferResponse(
        String transferId,
        String status,
        String customerId,
        String recipientName,
        String sendCurrency,
        String sendAmount,
        String fee,
        String totalDebit,
        String fxRate,
        String receiveCurrency,
        String receiveAmount,
        String partnerRef,
        String failureReason,
        String createdAt,
        String updatedAt
) {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.transferId(),
                t.status().name(),
                t.customerId(),
                t.recipientName(),
                t.sendCurrency(),
                t.sendAmount().toPlainString(),
                t.fee().toPlainString(),
                t.totalDebit().toPlainString(),
                t.fxRate().toPlainString(),
                t.receiveCurrency(),
                t.receiveAmount().toPlainString(),
                t.partnerRef(),
                t.failureReason(),
                TIMESTAMP.format(t.createdAt()),
                TIMESTAMP.format(t.updatedAt()));
    }

}
