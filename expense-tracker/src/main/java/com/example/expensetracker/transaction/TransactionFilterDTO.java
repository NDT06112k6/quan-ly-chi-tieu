package com.example.expensetracker.transaction;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class TransactionFilterDTO {

    public static final String MODEL_ATTRIBUTE = "filters";
    private static final int MINIMUM_YEAR = 1000;
    private static final int MAXIMUM_YEAR = 9999;

    @Size(max = 1000, message = "Từ khóa tối đa 1000 ký tự.")
    private String keyword;

    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            this.keyword = null;
            return;
        }
        this.keyword = keyword.strip();
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    @AssertTrue(message = "Khoảng ngày không hợp lệ: năm từ 1000 đến 9999 và ngày bắt đầu không sau ngày kết thúc.")
    public boolean isDateRangeValid() {
        if (!isSupportedDate(startDate) || !isSupportedDate(endDate)) {
            return false;
        }
        if (startDate == null || endDate == null) {
            return true;
        }
        boolean valid = !startDate.isAfter(endDate);
        return valid;
    }

    public boolean isActive() {
        boolean active = keyword != null || categoryId != null || startDate != null || endDate != null;
        return active;
    }

    private boolean isSupportedDate(LocalDate date) {
        if (date == null) {
            return true;
        }
        int year = date.getYear();
        boolean supported = year >= MINIMUM_YEAR && year <= MAXIMUM_YEAR;
        return supported;
    }
}
