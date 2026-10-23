package com.example.expensetracker.home;

import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardApiController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = {TransactionService.class, CurrentUserService.class, UserDetailsService.class})
class DashboardApiControllerTests {

    private final MockMvc mockMvc;
    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    @Autowired
    DashboardApiControllerTests(MockMvc mockMvc, TransactionService transactionService, CurrentUserService currentUserService) {
        this.mockMvc = mockMvc;
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(DashboardApiController.CHART_PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(transactionService, currentUserService);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,not-a-page", "size,0", "size,101"})
    void rejectsInvalidPaginationWithStructuredError(String field, String value) throws Exception {
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user("chart-test@example.invalid"))
                        .param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        verifyNoInteractions(transactionService, currentUserService);
    }

    @Test
    void returnsStructuredForbiddenForUnavailableAccount() throws Exception {
        when(currentUserService.getUserId(any(Authentication.class)))
                .thenThrow(new AccessDeniedException("private-diagnostic-marker"));
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user("chart-test@example.invalid")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(content().string(not(containsString("private-diagnostic-marker"))));
        verifyNoInteractions(transactionService);
    }

    @Test
    void doesNotReportDatabaseFailureAsEmptyChart() throws Exception {
        when(currentUserService.getUserId(any(Authentication.class))).thenReturn(42L);
        when(transactionService.getExpensesByCategory(any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("private-diagnostic-marker"));
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user("chart-test@example.invalid")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REPORT_UNAVAILABLE"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(content().string(not(containsString("private-diagnostic-marker"))));
    }
}
