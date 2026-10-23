package com.example.expensetracker.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionExportDTO(LocalDate occurredOn, String categoryName, BigDecimal amount, String note) {
}
