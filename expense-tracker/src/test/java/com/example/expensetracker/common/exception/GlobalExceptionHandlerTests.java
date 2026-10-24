package com.example.expensetracker.common.exception;

import com.example.expensetracker.common.error.ErrorResponse;
import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.home.HomeController;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = {UserDetailsService.class, TransactionService.class, CurrentUserService.class, UserService.class})
class GlobalExceptionHandlerTests {

    private static final String DIAGNOSTIC_MESSAGE = "private-diagnostic-marker <script>alert(1)</script>";
    private static final String TEST_USERNAME = "error-test@example.invalid";
    private final MockMvc mockMvc;
    private final CurrentUserService currentUserService;
    private final TransactionService transactionService;

    @Autowired
    GlobalExceptionHandlerTests(MockMvc mockMvc,
            CurrentUserService currentUserService, TransactionService transactionService) {
        this.mockMvc = mockMvc;
        this.currentUserService = currentUserService;
        this.transactionService = transactionService;
    }

    @Test
    void rendersFriendlyBadRequestWithoutExceptionDetails() throws Exception {
        when(currentUserService.getUserId(any(Authentication.class)))
                .thenThrow(new IllegalArgumentException(DIAGNOSTIC_MESSAGE));

        MvcResult result = mockMvc.perform(get("/").with(user(TEST_USERNAME)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/error"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Dữ liệu không hợp lệ")))
                .andExpect(content().string(containsString("href=\"/\"")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andReturn();

        assertSafeError(result, "INVALID_DATA");
        verifyNoInteractions(transactionService);
    }

    @Test
    void rendersFriendlyForbiddenPageWithoutExceptionDetails() throws Exception {
        when(currentUserService.getUserId(any(Authentication.class)))
                .thenThrow(new AccessDeniedException(DIAGNOSTIC_MESSAGE));

        MvcResult result = mockMvc.perform(get("/").with(user(TEST_USERNAME)))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/error"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Bạn không có quyền thực hiện thao tác này")))
                .andReturn();

        assertSafeError(result, "ACCESS_DENIED");
        verifyNoInteractions(transactionService);
    }

    @Test
    void preservesLoginRedirectForAnonymousRequests() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(currentUserService, transactionService);
    }

    @Test
    void doesNotTurnUnexpectedFailuresIntoHandledClientErrors() {
        when(currentUserService.getUserId(any(Authentication.class)))
                .thenThrow(new IllegalStateException(DIAGNOSTIC_MESSAGE));

        assertThatThrownBy(() -> mockMvc.perform(get("/").with(user(TEST_USERNAME))))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(transactionService);
    }

    private void assertSafeError(MvcResult result, String expectedCode) throws Exception {
        ErrorResponse error = (ErrorResponse) result.getModelAndView().getModel().get("error");
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.timestamp()).isNotNull();
        assertThat(error.message()).doesNotContain(DIAGNOSTIC_MESSAGE);
        String html = result.getResponse().getContentAsString();
        assertThat(html).doesNotContain("private-diagnostic-marker", "<script>", "java.lang.",
                "IllegalArgumentException", "AccessDeniedException", "org.springframework.");
    }
}
