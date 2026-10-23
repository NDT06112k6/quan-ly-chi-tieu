package com.example.expensetracker.home;

import java.math.BigDecimal;

public record CategoryExpenseDTO(String categoryName, BigDecimal totalAmount) {
}
