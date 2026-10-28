package com.example.expensetracker.budget;

import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.common.util.CategoryViewHelper;
import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BudgetController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = UserDetailsService.class)
class BudgetControllerTests {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;
    private static final String USERNAME = "budget-view@example.invalid";
    private final MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    @MockitoBean
    private CategoryViewHelper categoryViewHelper;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private UserService userService;

    @Autowired
    BudgetControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void configureUserAndCategoryOptions() {
        when(currentUserService.getUserId()).thenReturn(USER_ID);
        when(userService.getProfile(USERNAME)).thenReturn(new UserProfileDTO(USERNAME, "Minh"));
        CategoryDTO category = new CategoryDTO();
        category.setId(CATEGORY_ID);
        category.setName("Food");
        category.setType("CHI");
        doAnswer(invocation -> {
            Model formModel = invocation.getArgument(0);
            formModel.addAttribute("categories", List.of(category));
            formModel.addAttribute("hasMoreCategories", false);
            return null;
        }).when(categoryViewHelper).populateCategoryOptions(any(Model.class), eq(USER_ID), nullable(Long.class));
    }

    @Test
    void rendersNativeMonthAndWeekPickersWithOnlyMonthEnabledByDefaultAndNoPriceSuggestions() throws Exception {
        MvcResult result = mockMvc.perform(get("/budgets/new").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(view().name("budget/form"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(elementTag(html, "monthlyPeriod")).contains("type=\"month\"", "name=\"period\"",
                "required=\"required\"", "value=\"" + YearMonth.now() + "\"").doesNotContain("disabled");
        assertThat(elementTag(html, "weeklyPeriod")).contains("type=\"week\"", "name=\"period\"", "disabled=\"disabled\"")
                .doesNotContain("required");
        assertThat(elementTag(html, "monthly-period-field")).doesNotContain("hidden");
        assertThat(elementTag(html, "weekly-period-field")).contains("hidden");
        assertThat(html).contains("Theo tháng", "Theo tuần", "/js/budget-period.js", "name=\"_csrf\"");
        assertThat(html).doesNotContain("data-amount", "100.000", "500.000", "1.000.000", "5.000.000", "10.000.000");
        verifyNoInteractions(budgetService);
    }

    @Test
    void retainsWeeklySelectionAndPeriodAfterAnotherFieldFailsValidation() throws Exception {
        MvcResult result = mockMvc.perform(post("/budgets").with(user(USERNAME)).with(csrf())
                        .param("categoryId", CATEGORY_ID.toString()).param("type", "WEEKLY")
                        .param("period", "2026-W43").param("limitAmount", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("budget/form"))
                .andExpect(model().attributeHasFieldErrors(BudgetDTO.MODEL_ATTRIBUTE, "limitAmount"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(elementTag(html, "weeklyPeriod")).contains("type=\"week\"", "value=\"2026-W43\"", "required=\"required\"")
                .doesNotContain("disabled");
        assertThat(elementTag(html, "monthlyPeriod")).contains("disabled=\"disabled\"").doesNotContain("required");
        assertThat(elementTag(html, "weekly-period-field")).doesNotContain("hidden");
        assertThat(elementTag(html, "monthly-period-field")).contains("hidden");
        verifyNoInteractions(budgetService);
    }

    @Test
    void listDisplaysTypeAndReadablePeriodInsteadOfCanonicalCode() throws Exception {
        BudgetDTO monthly = budget(BudgetType.MONTHLY, "2026-10");
        BudgetDTO weekly = budget(BudgetType.WEEKLY, "2026-W43");
        when(budgetService.getBudgetsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(monthly, weekly)));
        doAnswer(invocation -> {
            Model listModel = invocation.getArgument(0);
            listModel.addAttribute("categoryNames", Map.of(CATEGORY_ID, "Food"));
            return null;
        }).when(categoryViewHelper).populateCategoryDetails(any(Model.class), eq(USER_ID), eq(Set.of(CATEGORY_ID)));

        MvcResult result = mockMvc.perform(get("/budgets").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(view().name("budget/list"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Loại", "Kỳ ngân sách", ">Tháng</td>", ">Tuần</td>",
                "Tháng 10, 2026", "Tuần 43, 2026", "100,000 ₫");
        assertThat(html).doesNotContain("2026-W43", ">2026-10</td>");
    }

    private BudgetDTO budget(BudgetType type, String period) {
        BudgetDTO dto = new BudgetDTO();
        dto.setType(type);
        dto.setPeriod(period);
        dto.setCategoryId(CATEGORY_ID);
        dto.setLimitAmount(new BigDecimal("100000.00"));
        return dto;
    }

    private String elementTag(String html, String id) {
        Pattern pattern = Pattern.compile("<[^>]+\\bid=\"" + Pattern.quote(id) + "\"[^>]*>");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).isTrue();
        String tag = matcher.group();
        return tag;
    }
}
