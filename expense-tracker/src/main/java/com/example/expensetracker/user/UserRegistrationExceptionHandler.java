package com.example.expensetracker.user;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = UserController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserRegistrationExceptionHandler {

    private static final String EMAIL_CONSTRAINT = "uk_users_email";
    private static final String REGISTRATION_ERROR_CODE = "registration.invalid";

    @ExceptionHandler(UserRegistrationException.class)
    public ModelAndView handleRegistrationException(
            UserRegistrationException exception, HttpServletRequest request) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setEmail(request.getParameter("email"));
        dto.setDisplayName(request.getParameter("displayName"));
        BindingResult bindingResult = new BeanPropertyBindingResult(dto, UserRegisterDTO.MODEL_ATTRIBUTE);
        bindingResult.rejectValue(exception.getFieldName(), REGISTRATION_ERROR_CODE, exception.getMessage());

        ModelAndView modelAndView = new ModelAndView(UserController.REGISTER_VIEW, bindingResult.getModel());
        return modelAndView;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDataIntegrityException(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        if (!isEmailConstraintViolation(exception)) {
            throw exception;
        }

        UserRegistrationException registrationException = UserRegistrationException.emailAlreadyExists();
        ModelAndView modelAndView = handleRegistrationException(registrationException, request);
        return modelAndView;
    }

    private boolean isEmailConstraintViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                String constraintName = constraintException.getConstraintName();
                boolean matchesEmailConstraint = EMAIL_CONSTRAINT.equals(constraintName)
                        || (constraintName != null && constraintName.endsWith("." + EMAIL_CONSTRAINT));
                return matchesEmailConstraint;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
