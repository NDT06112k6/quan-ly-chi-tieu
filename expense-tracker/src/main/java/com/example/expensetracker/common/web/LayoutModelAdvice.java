package com.example.expensetracker.common.web;

import com.example.expensetracker.budget.BudgetController;
import com.example.expensetracker.category.CategoryController;
import com.example.expensetracker.home.HomeController;
import com.example.expensetracker.transaction.TransactionController;
import com.example.expensetracker.user.UserController;
import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {
        HomeController.class, TransactionController.class, CategoryController.class,
        BudgetController.class, UserController.class
})
public class LayoutModelAdvice {

    private final UserService userService;

    public LayoutModelAdvice(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("layoutProfile")
    public UserProfileDTO loadLayoutProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        UserProfileDTO profile = userService.getProfile(authentication.getName());
        return profile;
    }
}
