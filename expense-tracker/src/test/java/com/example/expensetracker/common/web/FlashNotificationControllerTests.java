package com.example.expensetracker.common.web;

import com.example.expensetracker.budget.BudgetController;
import com.example.expensetracker.budget.BudgetService;
import com.example.expensetracker.category.CategoryController;
import com.example.expensetracker.category.CategoryService;
import com.example.expensetracker.common.util.CategoryViewHelper;
import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.transaction.TransactionController;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserProfileController;
import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CategoryController.class, TransactionController.class, BudgetController.class, UserProfileController.class})
@Import(SecurityConfig.class)
@MockitoBean(types = {UserDetailsService.class, CategoryService.class, TransactionService.class, BudgetService.class})
class FlashNotificationControllerTests {

    private static final String USERNAME = "notification-test@example.invalid";
    private static final Long USER_ID = 42L;
    private final MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private CategoryViewHelper categoryViewHelper;

    @Autowired
    FlashNotificationControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void configureUserAndCategoryOptions() {
        when(currentUserService.getUserId()).thenReturn(USER_ID);
        when(userService.getProfile(USERNAME)).thenReturn(new UserProfileDTO(USERNAME, "Minh"));
        doAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.addAttribute("categories", List.of());
            model.addAttribute("hasMoreCategories", false);
            return null;
        }).when(categoryViewHelper).populateCategoryOptions(any(Model.class), eq(USER_ID), nullable(Long.class));
    }

    @ParameterizedTest
    @CsvSource({
            "/categories, /categories, Thêm danh mục thành công!",
            "/transactions, /transactions, Thêm giao dịch thành công!",
            "/budgets, /budgets, Lưu ngân sách thành công!",
            "/profile/change-password, /profile, Đổi mật khẩu thành công."
    })
    void successfulActionsRedirectWithFlashText(String path, String destination, String message) throws Exception {
        mockMvc.perform(validRequest(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(destination))
                .andExpect(flash().attribute("success", message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categories", "/transactions", "/budgets", "/profile/change-password"})
    void validationFailuresKeepTheFormAndNeverFlashSuccess(String path) throws Exception {
        MvcResult result = mockMvc.perform(post(path).with(user(USERNAME)).with(csrf()))
                .andReturn();

        int expectedStatus = path.equals(UserProfileController.CHANGE_PASSWORD_PATH) ? 400 : 200;
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getFlashMap().isEmpty()).isTrue();
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getViewName()).doesNotStartWith("redirect:");
    }

    private MockHttpServletRequestBuilder validRequest(String path) {
        MockHttpServletRequestBuilder request = post(path).with(user(USERNAME)).with(csrf())
                .param("name", "Food")
                .param("type", "CHI")
                .param("amount", "12.34")
                .param("occurredOn", "2026-10-25")
                .param("categoryId", "7")
                .param("limitAmount", "100.00")
                .param("month", "2026-10")
                .param("oldPassword", "old-test-password")
                .param("newPassword", "new-test-password");
        return request;
    }
}
