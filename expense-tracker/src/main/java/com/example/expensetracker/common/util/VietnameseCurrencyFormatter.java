package com.example.expensetracker.common.util;

import org.thymeleaf.expression.Numbers;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public final class VietnameseCurrencyFormatter {

    private static final int MINIMUM_INTEGER_DIGITS = 1;
    private static final int FRACTION_DIGITS = 0;
    private static final String GROUPING_SEPARATOR = "COMMA";
    private static final String DECIMAL_SEPARATOR = "POINT";
    private static final String CURRENCY_SUFFIX = " ₫";

    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "Currency amount must not be null");
        Numbers numbers = new Numbers(Locale.ROOT);
        String formattedAmount = numbers.formatDecimal(amount, MINIMUM_INTEGER_DIGITS,
                GROUPING_SEPARATOR, FRACTION_DIGITS, DECIMAL_SEPARATOR);
        String currency = formattedAmount + CURRENCY_SUFFIX;
        return currency;
    }
}
