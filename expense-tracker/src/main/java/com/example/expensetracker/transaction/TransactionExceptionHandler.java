package com.example.expensetracker.transaction;

import com.example.expensetracker.common.error.ErrorResponse;
import com.example.expensetracker.common.util.ErrorResponseUtil;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@ControllerAdvice(assignableTypes = TransactionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransactionExceptionHandler {

    private static final String INVALID_CATEGORY_CODE = "INVALID_TRANSACTION_CATEGORY";
    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";
    private static final String INVALID_REQUEST_MESSAGE = "Thông tin yêu cầu không hợp lệ.";

    @ExceptionHandler(InvalidTransactionCategoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCategory(InvalidTransactionCategoryException exception) {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.FORBIDDEN, INVALID_CATEGORY_CODE, exception.getMessage());
        return response;
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.BAD_REQUEST, INVALID_REQUEST_CODE, INVALID_REQUEST_MESSAGE);
        return response;
    }

}
