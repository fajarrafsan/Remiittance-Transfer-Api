package com.finshot.remittance.model;

import java.math.BigDecimal;

public record TransferAmounts(
        BigDecimal sendAmount,
        BigDecimal fee,
        BigDecimal totalDebit,
        BigDecimal fxRate,
        BigDecimal receiveAmount
) {

}
