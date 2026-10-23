package com.example.expensetracker.budget;

import com.example.expensetracker.common.error.ErrorResponse;
import com.example.expensetracker.common.util.ErrorResponseUtil;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice(assignableTypes = BudgetController.class)
public class BudgetExceptionHandler {

    private static final String INVALID_CATEGORY_CODE = "INVALID_BUDGET_CATEGORY";
    private static final String CONFLICT_CODE = "BUDGET_CONFLICT";
    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";
    private static final String INVALID_REQUEST_MESSAGE = "Thông tin yêu cầu không hợp lệ.";

    @ExceptionHandler(InvalidBudgetCategoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCategory(InvalidBudgetCategoryException exception) {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.FORBIDDEN, INVALID_CATEGORY_CODE, exception.getMessage());
        return response;
    }

    @ExceptionHandler(BudgetConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(BudgetConflictException exception) {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.CONFLICT, CONFLICT_CODE, exception.getMessage());
        return response;
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.BAD_REQUEST, INVALID_REQUEST_CODE, INVALID_REQUEST_MESSAGE);
        return response;
    }
}
