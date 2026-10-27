package com.example.expensetracker.common.exception;

import com.example.expensetracker.category.CategoryInUseException;
import com.example.expensetracker.common.error.ErrorResponse;
import com.example.expensetracker.common.util.FlashMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_VIEW = "error/error";
    private static final String CATEGORY_LIST_REDIRECT = "redirect:/categories";
    private static final String ERROR_ATTRIBUTE = "error";
    private static final String INVALID_DATA_CODE = "INVALID_DATA";
    private static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";
    private static final String INVALID_DATA_MESSAGE =
            "Dữ liệu không hợp lệ. Vui lòng kiểm tra thông tin và thử lại.";
    private static final String ACCESS_DENIED_MESSAGE =
            "Bạn không có quyền thực hiện thao tác này. Vui lòng quay lại trang chủ.";

    @ExceptionHandler(CategoryInUseException.class)
    public String handleCategoryInUse(CategoryInUseException exception, RedirectAttributes redirectAttributes) {
        log.warn("Category deletion rejected because the category is in use.");
        FlashMessageUtil.addError(redirectAttributes, exception.getMessage());
        return CATEGORY_LIST_REDIRECT;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleIllegalArgumentException() {
        ModelAndView modelAndView = buildErrorPage(
                HttpStatus.BAD_REQUEST, INVALID_DATA_CODE, INVALID_DATA_MESSAGE);
        return modelAndView;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDeniedException() {
        ModelAndView modelAndView = buildErrorPage(
                HttpStatus.FORBIDDEN, ACCESS_DENIED_CODE, ACCESS_DENIED_MESSAGE);
        return modelAndView;
    }

    private ModelAndView buildErrorPage(HttpStatus status, String code, String message) {
        ErrorResponse errorResponse = new ErrorResponse(code, message, Instant.now());
        ModelAndView modelAndView = new ModelAndView(ERROR_VIEW);
        modelAndView.setStatus(status);
        modelAndView.addObject(ERROR_ATTRIBUTE, errorResponse);
        return modelAndView;
    }
}
