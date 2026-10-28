package com.example.expensetracker.budget;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@ValidBudgetPeriod
public class BudgetDTO {

    public static final String MODEL_ATTRIBUTE = "budgetDTO";
    private static final int YEAR_DIGITS = 4;
    private static final int PERIOD_NUMBER_DIGITS = 2;

    private Long id;

    @NotNull(message = "Vui lòng chọn danh mục.")
    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    @NotBlank(message = "Vui lòng nhập kỳ ngân sách.")
    @Size(max = 8, message = "Kỳ ngân sách không được vượt quá 8 ký tự.")
    private String period;

    @NotNull(message = "Vui lòng chọn loại ngân sách.")
    private BudgetType type;

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

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getPeriodLabel() {
        BudgetPeriod budgetPeriod = new BudgetPeriod(type, period);
        String canonicalPeriod = budgetPeriod.period();
        String year = canonicalPeriod.substring(0, YEAR_DIGITS);
        int numberOffset = canonicalPeriod.length() - PERIOD_NUMBER_DIGITS;
        int periodNumber = Integer.parseInt(canonicalPeriod.substring(numberOffset));
        String label = type.getDisplayName() + " " + periodNumber + ", " + year;
        return label;
    }

    public BudgetType getType() {
        return type;
    }

    public void setType(BudgetType type) {
        this.type = type;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }
}
