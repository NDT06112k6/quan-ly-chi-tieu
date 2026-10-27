package com.example.expensetracker.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class VietnameseCurrencyFormatterTests {

    private final VietnameseCurrencyFormatter formatter = new VietnameseCurrencyFormatter();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0.00 | 0 ₫",
            "0.10 | 0 ₫",
            "50000 | 50,000 ₫",
            "50000.49 | 50,000 ₫",
            "50000.50 | 50,000 ₫",
            "50001.50 | 50,002 ₫",
            "50000.51 | 50,001 ₫",
            "-1500.75 | -1,501 ₫",
            "9999999999999.99 | 10,000,000,000,000 ₫",
            "12345678901234567890.00 | 12,345,678,901,234,567,890 ₫"
    })
    void formatsDongWithoutChangingStoredPrecision(String value, String expected) {
        BigDecimal amount = new BigDecimal(value);

        String formatted = formatter.format(amount);

        assertThat(formatted).isEqualTo(expected);
        assertThat(amount.toPlainString()).isEqualTo(value);
    }

    @Test
    void rejectsMissingAmountsInsteadOfDisplayingZero() {
        assertThatNullPointerException().isThrownBy(() -> formatter.format(null));
    }
}
