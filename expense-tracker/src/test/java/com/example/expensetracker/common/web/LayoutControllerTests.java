package com.example.expensetracker.common.web;

import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.home.DashboardDTO;
import com.example.expensetracker.home.HomeController;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserController;
import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HomeController.class, UserController.class})
@Import(SecurityConfig.class)
@MockitoBean(types = UserDetailsService.class)
class LayoutControllerTests {

    private static final String USERNAME = "layout-test@example.invalid";
    private static final Long USER_ID = 42L;
    private final MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    LayoutControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/register"})
    void rendersPublicFormsInTheSharedLayoutWithoutAuthenticatedNavigation(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertSharedDocument(html);
        assertThat(html).contains("is-guest", "Expense Tracker", "name=\"_csrf\"");
        assertThat(html).doesNotContain("id=\"app-sidebar\"", "action=\"/logout\"", "data-testid=\"sidebar-user\"");
        verifyNoInteractions(userService, currentUserService, transactionService);
    }

    @Test
    void rendersAuthenticatedNavigationWithEscapedDisplayNameAndCsrfProtectedLogout() throws Exception {
        stubDashboard("Minh <script>alert(1)</script>");

        MvcResult result = mockMvc.perform(get("/").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertSharedDocument(html);
        assertThat(html).contains("is-signed-in", "id=\"app-sidebar\"", "Điều hướng chính",
                "href=\"/transactions\"", "href=\"/categories\"", "href=\"/budgets\"", "href=\"/profile\"",
                "method=\"post\" action=\"/logout\"", "name=\"_csrf\"", "data-bs-target=\"#app-sidebar\"");
        assertThat(html)
                .containsOnlyOnce("aria-current=\"page\"")
                .containsOnlyOnce("action=\"/logout\"");
        assertThat(html).contains("Minh &lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("Minh <script>");
        verify(userService).getProfile(USERNAME);
    }

    @Test
    void servesSharedStylesWithoutAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(".app-sidebar", "@media", ".is-guest");
        verifyNoInteractions(userService, currentUserService, transactionService);
    }

    private void stubDashboard(String displayName) {
        LocalDate today = LocalDate.now();
        DashboardDTO dashboard = new DashboardDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(userService.getProfile(USERNAME)).thenReturn(new UserProfileDTO(USERNAME, displayName));
        when(currentUserService.getUserId(any(Authentication.class))).thenReturn(USER_ID);
        when(transactionService.getDashboardData(USER_ID, today.getMonthValue(), today.getYear()))
                .thenReturn(dashboard);
    }

    private void assertSharedDocument(String html) {
        assertThat(html.stripLeading()).startsWith("<!DOCTYPE html>");
        assertThat(html)
                .containsOnlyOnce("<html")
                .containsOnlyOnce("<head>")
                .containsOnlyOnce("<body")
                .containsOnlyOnce("id=\"main-content\"")
                .containsOnlyOnce("bootstrap@5.3.8/dist/css");
        assertThat(html).contains("/css/app.css", "bootstrap.bundle.min.js");
        assertThat(html).doesNotContain("th:replace", "th:fragment", "<th:block");
    }
}
