package com.example.expensetracker.transaction;

public class InvalidTransactionCategoryException extends RuntimeException {

    private static final String MESSAGE = "Danh mục không tồn tại hoặc không thuộc quyền sở hữu của bạn.";

    public InvalidTransactionCategoryException() {
        super(MESSAGE);
    }
}
