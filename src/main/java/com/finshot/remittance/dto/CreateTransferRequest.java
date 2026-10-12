package com.finshot.remittance.dto;

public record CreateTransferRequest(
        String customerId,
        String sendCurrency,
        String sendAmount,
        String receiveCurrency,
        String recipientName
) {

}


