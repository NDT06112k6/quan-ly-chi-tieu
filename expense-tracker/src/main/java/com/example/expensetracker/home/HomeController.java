package com.example.expensetracker.home;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final String HOME_URL = "/";
    private static final String INDEX_VIEW = "index";

    @GetMapping(HOME_URL)
    public String showHomePage() {
        return INDEX_VIEW;
    }
}
