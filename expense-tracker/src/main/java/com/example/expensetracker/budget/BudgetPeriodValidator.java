package com.example.expensetracker.budget;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BudgetPeriodValidator implements ConstraintValidator<ValidBudgetPeriod, BudgetDTO> {

    @Override
    public boolean isValid(BudgetDTO dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getType() == null || dto.getPeriod() == null || dto.getPeriod().isBlank()) {
            return true;
        }
        if (BudgetPeriod.isValid(dto.getType(), dto.getPeriod())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("period")
                .addConstraintViolation();
        return false;
    }
}
