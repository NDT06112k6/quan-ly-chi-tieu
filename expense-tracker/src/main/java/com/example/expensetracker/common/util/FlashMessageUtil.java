package com.example.expensetracker.common.util;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public final class FlashMessageUtil {

    private static final String SUCCESS_ATTRIBUTE = "success";

    private FlashMessageUtil() {
    }

    public static void addSuccess(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(SUCCESS_ATTRIBUTE, message);
    }
}
