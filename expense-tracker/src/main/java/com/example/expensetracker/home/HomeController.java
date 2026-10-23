package com.example.expensetracker.home;

import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class HomeController {

    private static final String HOME_URL = "/";
    private static final String INDEX_VIEW = "index";

    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    public HomeController(TransactionService transactionService, CurrentUserService currentUserService) {
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(HOME_URL)
    public String showHomePage(Model model, Authentication authentication) {
        LocalDate currentDate = LocalDate.now();
        Long userId = currentUserService.getUserId(authentication);
        int month = currentDate.getMonthValue();
        int year = currentDate.getYear();
        DashboardDTO dashboard = transactionService.getDashboardData(userId, month, year);

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("currentMonth", month);
        model.addAttribute("currentYear", year);
        return INDEX_VIEW;
    }
}
