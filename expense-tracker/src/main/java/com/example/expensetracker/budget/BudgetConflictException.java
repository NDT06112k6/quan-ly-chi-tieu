package com.example.expensetracker.budget;

public class BudgetConflictException extends RuntimeException {

    private static final String MESSAGE = "Ngân sách đã thay đổi. Vui lòng tải lại và thử lại.";

    public BudgetConflictException() {
        super(MESSAGE);
    }
}
