package com.example.expensetracker.common.util;

import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

public final class FormViewUtil {

    private FormViewUtil() {
    }

    public static Model getBindingModel(BindingResult bindingResult) {
        Model model = new ExtendedModelMap();
        model.addAllAttributes(bindingResult.getModel());
        return model;
    }
}
