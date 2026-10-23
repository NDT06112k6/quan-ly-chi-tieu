package com.example.expensetracker.budget;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class BudgetDTO {

    public static final String MODEL_ATTRIBUTE = "budgetDTO";
    private static final String MONTH_PATTERN = "[1-9]\\d{3}-(0[1-9]|1[0-2])";

    private Long id;

    @NotNull(message = "Vui lòng chọn danh mục.")
    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    @NotBlank(message = "Vui lòng chọn tháng.")
    @Pattern(regexp = MONTH_PATTERN, message = "Tháng phải có định dạng yyyy-MM, năm từ 1000 đến 9999.")
    private String month;

    @NotNull(message = "Vui lòng nhập hạn mức ngân sách.")
    @Positive(message = "Hạn mức ngân sách phải lớn hơn 0.")
    @Digits(integer = 13, fraction = 2, message = "Hạn mức tối đa 13 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal limitAmount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }
}
