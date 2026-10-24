package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDetailDTO(Long id, BigDecimal amount, LocalDate occurredOn,
        String categoryName, CategoryType categoryType, String note) {

    public boolean isExpense() {
        boolean expense = categoryType == CategoryType.CHI;
        return expense;
    }
}
