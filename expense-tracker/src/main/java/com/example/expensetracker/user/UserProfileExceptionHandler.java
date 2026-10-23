package com.example.expensetracker.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = UserProfileController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserProfileExceptionHandler {

    private static final String PASSWORD_ERROR_CODE = "password.change.invalid";

    @ExceptionHandler(UserPasswordChangeException.class)
    public ModelAndView handlePasswordChange(UserPasswordChangeException exception, HttpServletRequest request) {
        Object profile = request.getAttribute(UserProfileDTO.MODEL_ATTRIBUTE);
        if (!(profile instanceof UserProfileDTO)) {
            throw exception;
        }
        PasswordChangeDTO dto = new PasswordChangeDTO();
        BindingResult bindingResult = new BeanPropertyBindingResult(dto, PasswordChangeDTO.MODEL_ATTRIBUTE);
        bindingResult.rejectValue(exception.getFieldName(), PASSWORD_ERROR_CODE, exception.getMessage());
        ModelAndView modelAndView = new ModelAndView(UserProfileController.PROFILE_VIEW, bindingResult.getModel());
        modelAndView.addObject(UserProfileDTO.MODEL_ATTRIBUTE, profile);
        modelAndView.setStatus(HttpStatus.BAD_REQUEST);
        return modelAndView;
    }
}
