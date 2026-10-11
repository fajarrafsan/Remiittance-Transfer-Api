package com.finshot.remittance.service;

import com.finshot.remittance.model.TransferAmounts;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class TransferCalculatorTest {

    private final TransferCalculator calculator = new TransferCalculator();

    @Test
    void briefExample() {
        TransferAmounts a = calculator.calculate(new BigDecimal("500000"));
        assertThat(a.fee().toPlainString()).isEqualTo("5000");
        assertThat(a.totalDebit().toPlainString()).isEqualTo("505000");
        assertThat(a.receiveAmount().toPlainString()).isEqualTo("20600.00");
    }

    @Test
    void minimumFeeApplies() {
        TransferAmounts a = calculator.calculate(new BigDecimal("10000"));
        assertThat(a.fee().toPlainString()).isEqualTo("3000");
        assertThat(a.totalDebit().toPlainString()).isEqualTo("13000");
        assertThat(a.receiveAmount().toPlainString()).isEqualTo("412.00");
    }

    @Test
    void feeRoundsHalfUp() {
        TransferAmounts a = calculator.calculate(new BigDecimal("300050"));
        assertThat(a.fee().toPlainString()).isEqualTo("3001");
        assertThat(a.totalDebit().toPlainString()).isEqualTo("303051");
        assertThat(a.receiveAmount().toPlainString()).isEqualTo("12362.06");
    }
}
