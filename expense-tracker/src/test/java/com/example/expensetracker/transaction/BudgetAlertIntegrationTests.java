package com.example.expensetracker.transaction;

import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetAlertIntegrationTests {

    private static final String CATEGORY_NAME = "Food <script>alert(1)</script>";

    private final MockMvc mockMvc;
    private final EntityManager entityManager;
    private final TransactionRepository transactionRepository;

    private YearMonth month;
    private User owner;
    private User otherUser;
    private Category expenseCategory;
    private Category incomeCategory;
    private Category foreignCategory;

    @Autowired
    BudgetAlertIntegrationTests(MockMvc mockMvc, EntityManager entityManager, TransactionRepository transactionRepository) {
        this.mockMvc = mockMvc;
        this.entityManager = entityManager;
        this.transactionRepository = transactionRepository;
    }

    @BeforeEach
    void createAccountsAndCategories() {
        month = YearMonth.now();
        String suffix = UUID.randomUUID().toString();
        owner = new User("alert-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        otherUser = new User("alert-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(otherUser);
        expenseCategory = new Category(owner.getId(), CATEGORY_NAME, CategoryType.CHI);
        incomeCategory = new Category(owner.getId(), "Income", CategoryType.THU);
        foreignCategory = new Category(otherUser.getId(), CATEGORY_NAME, CategoryType.CHI);
        entityManager.persist(expenseCategory);
        entityManager.persist(incomeCategory);
        entityManager.persist(foreignCategory);
        entityManager.flush();
    }

    @Test
    void includesSavedExpenseAndCurrentMonthBoundariesWhileExcludingOtherMonthsCategoriesAndUsers() throws Exception {
        persistBudget(expenseCategory, month, "100.10");
        persistTransaction(expenseCategory, "60.10", month.atDay(1));
        persistTransaction(expenseCategory, "40.00", month.atEndOfMonth());
        persistTransaction(expenseCategory, "999.00", month.minusMonths(1).atEndOfMonth());
        persistTransaction(expenseCategory, "999.00", month.plusMonths(1).atDay(1));
        persistTransaction(foreignCategory, "999.00", month.atDay(1));
        Category otherOwnedCategory = new Category(owner.getId(), "Other expense", CategoryType.CHI);
        entityManager.persist(otherOwnedCategory);
        persistTransaction(otherOwnedCategory, "999.00", month.atDay(1));
        entityManager.flush();

        MvcResult result = mockMvc.perform(createRequest(expenseCategory, "0.10")
                        .param("userId", otherUser.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"))
                .andExpect(flash().attribute("warning", expectedWarning("100.20", "100.10", "0.10")))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"))
                .andReturn();

        BigDecimal total = transactionRepository.sumExpensesByUserIdAndCategoryIdAndMonth(
                owner.getId(), expenseCategory.getId(), month);
        assertThat(total).isEqualByComparingTo("100.20");
        assertWarningShownOnlyOnce(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"49.99", "50.00"})
    void doesNotWarnAtOrBelowLimit(String amount) throws Exception {
        persistBudget(expenseCategory, month, "100.00");
        persistTransaction(expenseCategory, "50.00", month.atDay(1));
        entityManager.flush();

        mockMvc.perform(createRequest(expenseCategory, amount))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"));
    }

    @Test
    void doesNotUseAnotherUsersBudgetOrBudgetsFromOtherMonths() throws Exception {
        persistBudget(foreignCategory, month, "0.01");
        persistBudget(expenseCategory, month.minusMonths(1), "0.01");
        persistBudget(expenseCategory, month.plusMonths(1), "0.01");
        entityManager.flush();

        mockMvc.perform(createRequest(expenseCategory, "100.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"));
    }

    @Test
    void doesNotWarnForIncomeEvenWhenItsAmountExceedsConfiguredLimit() throws Exception {
        persistBudget(incomeCategory, month, "0.01");
        entityManager.flush();

        mockMvc.perform(createRequest(incomeCategory, "100.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"));
    }

    @Test
    void warnsWhenFirstExpenseAloneExceedsLimit() throws Exception {
        persistBudget(expenseCategory, month, "0.05");
        entityManager.flush();

        mockMvc.perform(createRequest(expenseCategory, "0.10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", expectedWarning("0.10", "0.05", "0.05")))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"));
    }

    @Test
    void doesNotIncludeNewTransactionFromAnotherMonthInCurrentMonthExpenseTotal() throws Exception {
        persistBudget(expenseCategory, month, "100.00");
        persistBudget(expenseCategory, month.minusMonths(1), "0.01");
        persistTransaction(expenseCategory, "50.00", month.atDay(1));
        entityManager.flush();

        mockMvc.perform(createRequest(expenseCategory, "1000.00", month.minusMonths(1).atDay(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"));
    }

    @Test
    void deniesForeignCategoryWithoutSavingOrExposingBudgetWarning() throws Exception {
        persistBudget(foreignCategory, month, "0.01");
        entityManager.flush();

        mockMvc.perform(createRequest(foreignCategory, "100.00"))
                .andExpect(status().isForbidden())
                .andExpect(flash().attributeCount(0));

        BigDecimal total = transactionRepository.sumExpensesByUserIdAndCategoryIdAndMonth(
                otherUser.getId(), foreignCategory.getId(), month);
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void assertWarningShownOnlyOnce(MvcResult creation) throws Exception {
        MockHttpSession session = (MockHttpSession) creation.getRequest().getSession(false);
        MvcResult redirected = mockMvc.perform(get("/transactions").with(user(owner.getEmail())).session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = redirected.getResponse().getContentAsString();
        assertThat(html).contains("id=\"toast-warning\"", "data-toast-icon=\"warning\"", "vượt 0.10.");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)</script>");

        MvcResult refreshed = mockMvc.perform(get("/transactions").with(user(owner.getEmail())).session(session))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(refreshed.getResponse().getContentAsString()).doesNotContain("id=\"toast-warning\"");
    }

    private MockHttpServletRequestBuilder createRequest(Category category, String amount) {
        MockHttpServletRequestBuilder request = createRequest(category, amount, month.atDay(1));
        return request;
    }

    private MockHttpServletRequestBuilder createRequest(Category category, String amount, LocalDate date) {
        MockHttpServletRequestBuilder request = post("/transactions").with(user(owner.getEmail())).with(csrf())
                .param("categoryId", category.getId().toString())
                .param("amount", amount)
                .param("occurredOn", date.toString());
        return request;
    }

    private void persistBudget(Category category, YearMonth budgetMonth, String limit) {
        Budget budget = new Budget(category.getUserId(), category.getId(), budgetMonth.atDay(1), new BigDecimal(limit));
        entityManager.persist(budget);
    }

    private void persistTransaction(Category category, String amount, LocalDate date) {
        Transaction transaction = new Transaction(category.getUserId(), category.getId(), new BigDecimal(amount), date, null);
        entityManager.persist(transaction);
    }

    private String expectedWarning(String total, String limit, String exceeded) {
        String message = "Danh mục \"" + CATEGORY_NAME + "\" đã vượt ngân sách tháng " + month
                + ": tổng chi " + total + ", hạn mức " + limit + ", vượt " + exceeded + ".";
        return message;
    }
}
