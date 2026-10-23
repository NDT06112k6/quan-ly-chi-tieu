package com.example.expensetracker.budget;

public class InvalidBudgetCategoryException extends RuntimeException {

    private static final String MESSAGE = "Danh mục không tồn tại hoặc không thuộc quyền sở hữu của bạn.";

    public InvalidBudgetCategoryException() {
        super(MESSAGE);
    }
}
