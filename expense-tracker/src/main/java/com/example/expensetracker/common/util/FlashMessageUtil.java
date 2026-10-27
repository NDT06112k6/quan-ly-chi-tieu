package com.example.expensetracker.common.util;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

public final class FlashMessageUtil {

    private static final String SUCCESS_ATTRIBUTE = "success";
    private static final String ERROR_ATTRIBUTE = "error";
    private static final String WARNING_ATTRIBUTE = "warning";

    private FlashMessageUtil() {
    }

    public static void addSuccess(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(SUCCESS_ATTRIBUTE, message);
    }

    public static void addError(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(ERROR_ATTRIBUTE, message);
    }

    public static void addWarning(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(WARNING_ATTRIBUTE, message);
    }

    public static void addWarningIfPresent(RedirectAttributes redirectAttributes, Optional<String> warning) {
        if (warning.isPresent()) {
            addWarning(redirectAttributes, warning.get());
        }
    }
}
