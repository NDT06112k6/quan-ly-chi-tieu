package com.example.expensetracker.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionDTO {

    public static final String MODEL_ATTRIBUTE = "transactionDTO";

    private Long id;

    @NotNull(message = "Vui lòng nhập số tiền.")
    @Positive(message = "Số tiền phải lớn hơn 0.")
    @Digits(integer = 13, fraction = 2,
            message = "Số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal amount;

    @NotNull(message = "Vui lòng chọn ngày giao dịch.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate occurredOn;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự.")
    private String note;

    @NotNull(message = "Vui lòng chọn danh mục.")
    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public void setOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
