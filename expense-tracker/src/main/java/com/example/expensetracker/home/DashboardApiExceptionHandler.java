package com.example.expensetracker.home;

import com.example.expensetracker.common.error.ErrorResponse;
import com.example.expensetracker.common.util.ErrorResponseUtil;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = DashboardApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DashboardApiExceptionHandler {

    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";
    private static final String INVALID_REQUEST_MESSAGE = "Thông tin yêu cầu không hợp lệ.";
    private static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";
    private static final String ACCESS_DENIED_MESSAGE = "Bạn không có quyền xem báo cáo này.";
    private static final String REPORT_ERROR_CODE = "REPORT_UNAVAILABLE";
    private static final String REPORT_ERROR_MESSAGE = "Không thể tải báo cáo. Vui lòng thử lại.";

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.BAD_REQUEST, INVALID_REQUEST_CODE, INVALID_REQUEST_MESSAGE);
        return response;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied() {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.FORBIDDEN, ACCESS_DENIED_CODE, ACCESS_DENIED_MESSAGE);
        return response;
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessFailure() {
        ResponseEntity<ErrorResponse> response = ErrorResponseUtil.build(
                HttpStatus.INTERNAL_SERVER_ERROR, REPORT_ERROR_CODE, REPORT_ERROR_MESSAGE);
        return response;
    }
}
