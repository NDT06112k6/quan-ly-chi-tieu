package com.example.expensetracker.category;

public class CategoryInUseException extends RuntimeException {

    private static final String MESSAGE = "Không thể xóa danh mục đang có giao dịch hoặc ngân sách.";

    public CategoryInUseException() {
        super(MESSAGE);
    }
}
