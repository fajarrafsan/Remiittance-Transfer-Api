package com.finshot.remittance.service;

import com.finshot.remittance.model.TransferAmounts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TransferCalculator {

    static final BigDecimal FEE_RATE = new BigDecimal("0.01");
    static final BigDecimal MIN_FEE = new BigDecimal("3000");
    static final BigDecimal FX_RATE = new BigDecimal("0.0412");

    public TransferAmounts calculate(BigDecimal sendAmount) {
        BigDecimal fee = sendAmount.multiply(FEE_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .max(MIN_FEE);
        BigDecimal totalDebit = sendAmount.add(fee);
        BigDecimal receiveAmount = sendAmount.multiply(FX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        return new TransferAmounts(sendAmount, fee, totalDebit, FX_RATE, receiveAmount);
    }
}
