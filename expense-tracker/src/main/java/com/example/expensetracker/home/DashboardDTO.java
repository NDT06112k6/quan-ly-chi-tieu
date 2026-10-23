package com.example.expensetracker.home;

import java.math.BigDecimal;

public class DashboardDTO {

    private final BigDecimal totalIncome;
    private final BigDecimal totalExpense;
    private final BigDecimal balance;

    public DashboardDTO(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance) {
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.balance = balance;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
