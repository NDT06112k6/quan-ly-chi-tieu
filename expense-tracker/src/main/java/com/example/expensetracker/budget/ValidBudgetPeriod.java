package com.example.expensetracker.budget;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BudgetPeriodValidator.class)
public @interface ValidBudgetPeriod {
    String message() default "Kỳ ngân sách không hợp lệ: tháng dùng yyyy-MM, tuần dùng yyyy-Www theo ISO.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
