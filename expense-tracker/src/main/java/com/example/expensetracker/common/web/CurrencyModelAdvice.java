package com.example.expensetracker.common.web;

import com.example.expensetracker.common.util.VietnameseCurrencyFormatter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrencyModelAdvice {

    private static final VietnameseCurrencyFormatter CURRENCY_FORMATTER = new VietnameseCurrencyFormatter();

    @ModelAttribute("vnCurrency")
    public VietnameseCurrencyFormatter currencyFormatter() {
        return CURRENCY_FORMATTER;
    }
}
