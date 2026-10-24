package com.example.expensetracker.home;

import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = {UserDetailsService.class, TransactionService.class, CurrentUserService.class, UserService.class})
class HomeControllerTests {

    private final MockMvc mockMvc;
    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    @Autowired
    HomeControllerTests(MockMvc mockMvc, TransactionService transactionService, CurrentUserService currentUserService) {
        this.mockMvc = mockMvc;
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @Test
    void redirectsAnonymousVisitorsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(transactionService, currentUserService);
    }

    @Test
    void rendersWelcomeMessageAndCsrfProtectedLogoutForm() throws Exception {
        LocalDate currentDate = LocalDate.now();
        Long userId = 42L;
        DashboardDTO dashboard = new DashboardDTO(
                new BigDecimal("100.10"), new BigDecimal("20.05"), new BigDecimal("80.05"));
        when(currentUserService.getUserId(any(Authentication.class))).thenReturn(userId);
        when(transactionService.getDashboardData(userId, currentDate.getMonthValue(), currentDate.getYear()))
                .thenReturn(dashboard);

        mockMvc.perform(get("/").with(user("home-test@example.invalid")))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("dashboard", dashboard))
                .andExpect(model().attribute("currentMonth", currentDate.getMonthValue()))
                .andExpect(model().attribute("currentYear", currentDate.getYear()))
                .andExpect(content().string(containsString("Chào mừng đến với Expense Tracker")))
                .andExpect(content().string(containsString("Thêm giao dịch ngay")))
                .andExpect(content().string(containsString("chart.js@4.5.1/dist/chart.umd.min.js")))
                .andExpect(content().string(containsString("/js/dashboard-chart.js")))
                .andExpect(content().string(containsString("id=\"expense-chart\"")))
                .andExpect(content().string(containsString("method=\"post\" action=\"/logout\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Đăng xuất")));

        verify(transactionService).getDashboardData(userId, currentDate.getMonthValue(), currentDate.getYear());
    }
}
