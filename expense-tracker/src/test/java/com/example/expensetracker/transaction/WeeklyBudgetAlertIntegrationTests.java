package com.example.expensetracker.transaction;

import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetType;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WeeklyBudgetAlertIntegrationTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 25);
    private static final BudgetPeriod WEEK = new BudgetPeriod(BudgetType.WEEKLY, "2026-W43");
    private final MockMvc mockMvc;
    private final EntityManager entityManager;
    private final TransactionRepository transactionRepository;
    private User owner;
    private Category category;
    private Category foreignCategory;

    @Autowired
    WeeklyBudgetAlertIntegrationTests(MockMvc mockMvc, EntityManager entityManager, TransactionRepository transactionRepository) {
        this.mockMvc = mockMvc;
        this.entityManager = entityManager;
        this.transactionRepository = transactionRepository;
    }

    @BeforeEach
    void createAccountsAndCategories() {
        String suffix = UUID.randomUUID().toString();
        owner = new User("weekly-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        User foreignOwner = new User("weekly-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(foreignOwner);
        category = new Category(owner.getId(), "Food", CategoryType.CHI);
        foreignCategory = new Category(foreignOwner.getId(), "Food", CategoryType.CHI);
        entityManager.persist(category);
        entityManager.persist(foreignCategory);
        entityManager.flush();
    }

    @Test
    void includesMondayAndSundayAndExcludesAdjacentDatesCategoriesAndUsers() throws Exception {
        persistBudget(category, WEEK, "100.00");
        persistTransaction(category, "60.10", MONDAY);
        persistTransaction(category, "39.90", SUNDAY);
        persistTransaction(category, "999.00", MONDAY.minusDays(1));
        persistTransaction(category, "999.00", SUNDAY.plusDays(1));
        persistTransaction(foreignCategory, "999.00", MONDAY);
        Category otherCategory = new Category(owner.getId(), "Other", CategoryType.CHI);
        entityManager.persist(otherCategory);
        persistTransaction(otherCategory, "999.00", MONDAY);

        mockMvc.perform(createRequest("0.10", SUNDAY).param("userId", foreignCategory.getUserId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", "Danh mục \"Food\" đã vượt ngân sách tuần 2026-W43: "
                        + "tổng chi 100.10, hạn mức 100.00, vượt 0.10."));

        assertExpenseTotal(category, WEEK, "100.10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"49.99", "50.00"})
    void doesNotWarnAtOrBelowWeeklyLimit(String amount) throws Exception {
        persistBudget(category, WEEK, "100.00");
        persistTransaction(category, "50.00", MONDAY);

        mockMvc.perform(createRequest(amount, SUNDAY))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("success", "Thêm giao dịch thành công!"));
    }

    @Test
    void warnsForMonthlyAndWeeklyBudgetsAtTheSameTime() throws Exception {
        persistBudget(category, WEEK, "20.00");
        persistBudget(category, new BudgetPeriod(BudgetType.MONTHLY, "2026-10"), "100.00");
        persistTransaction(category, "25.00", MONDAY);
        persistTransaction(category, "40.00", MONDAY.minusDays(1));

        mockMvc.perform(createRequest("50.00", SUNDAY))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", containsString("tháng 2026-10: tổng chi 115.00")))
                .andExpect(flash().attribute("warning", containsString("tuần 2026-W43: tổng chi 75.00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-12-28", "2021-01-01", "2021-01-03"})
    void usesIsoWeekBasedYearForAWeekCrossingCalendarYears(String dateValue) throws Exception {
        BudgetPeriod period = new BudgetPeriod(BudgetType.WEEKLY, "2020-W53");
        persistBudget(category, period, "100.00");
        persistTransaction(category, "60.00", LocalDate.of(2020, 12, 28));
        persistTransaction(category, "40.00", LocalDate.of(2021, 1, 3));

        mockMvc.perform(createRequest("0.10", LocalDate.parse(dateValue)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", containsString("tuần 2020-W53: tổng chi 100.10")));

        assertExpenseTotal(category, period, "100.10");
    }

    @Test
    void editingIntoAnotherWeekOfSameMonthChecksBothWeeksAndMonthlyBudgetOnce() throws Exception {
        BudgetPeriod nextWeek = new BudgetPeriod(BudgetType.WEEKLY, "2026-W44");
        persistBudget(category, WEEK, "100.00");
        persistBudget(category, nextWeek, "100.00");
        persistBudget(category, new BudgetPeriod(BudgetType.MONTHLY, "2026-10"), "100.00");
        persistTransaction(category, "110.00", MONDAY);
        persistTransaction(category, "95.00", MONDAY.plusWeeks(1));
        Transaction moved = persistTransaction(category, "10.00", MONDAY.plusDays(1));

        MvcResult result = mockMvc.perform(editRequest(moved, MONDAY.plusWeeks(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", containsString("tuần 2026-W43: tổng chi 110.00")))
                .andExpect(flash().attribute("warning", containsString("tuần 2026-W44: tổng chi 105.00")))
                .andReturn();

        String warning = (String) result.getFlashMap().get("warning");
        assertThat(warning).containsOnlyOnce("ngân sách tháng 2026-10");
        assertExpenseTotal(category, WEEK, "110.00");
        assertExpenseTotal(category, nextWeek, "105.00");
    }

    @Test
    void editingAcrossMonthsInSameIsoWeekChecksOldMonthAndAvoidsDuplicateWeekWarning() throws Exception {
        BudgetPeriod week = new BudgetPeriod(BudgetType.WEEKLY, "2026-W44");
        persistBudget(category, week, "100.00");
        persistBudget(category, new BudgetPeriod(BudgetType.MONTHLY, "2026-10"), "100.00");
        persistBudget(category, new BudgetPeriod(BudgetType.MONTHLY, "2026-11"), "100.00");
        persistTransaction(category, "110.00", LocalDate.of(2026, 10, 26));
        Transaction moved = persistTransaction(category, "10.00", LocalDate.of(2026, 10, 31));

        MvcResult result = mockMvc.perform(editRequest(moved, LocalDate.of(2026, 11, 1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", containsString("tháng 2026-10: tổng chi 110.00")))
                .andReturn();

        String warning = (String) result.getFlashMap().get("warning");
        assertThat(warning).containsOnlyOnce("ngân sách tuần 2026-W44");
        assertThat(warning).contains("tổng chi 120.00");
        assertExpenseTotal(category, week, "120.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "90.00", "110.00"})
    void deletionChecksRemainingWeeklyExpenses(String remaining) throws Exception {
        persistBudget(category, WEEK, "100.00");
        if (new BigDecimal(remaining).signum() > 0) {
            persistTransaction(category, remaining, MONDAY);
        }
        Transaction deleted = persistTransaction(category, "110.00", SUNDAY);

        MvcResult result = mockMvc.perform(post("/transactions/{id}/delete", deleted.getId())
                        .with(user(owner.getEmail())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        if (new BigDecimal(remaining).compareTo(new BigDecimal("100.00")) > 0) {
            assertThat((String) result.getFlashMap().get("warning")).contains("tuần 2026-W43: tổng chi 110.00");
        } else {
            assertThat(result.getFlashMap().containsKey("warning")).isFalse();
        }
        assertThat(transactionRepository.findByIdAndUserId(deleted.getId(), owner.getId())).isEmpty();
        assertExpenseTotal(category, WEEK, remaining);
    }

    @Test
    void doesNotUseAnotherUsersWeeklyBudget() throws Exception {
        persistBudget(foreignCategory, WEEK, "0.01");

        mockMvc.perform(createRequest("100.00", MONDAY))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000-01-01", "9999-12-31"})
    void weeklyQueriesStayWithinTheSupportedStorageDateRange(String dateValue) throws Exception {
        LocalDate date = LocalDate.parse(dateValue);
        BudgetPeriod period = BudgetPeriod.containing(BudgetType.WEEKLY, date);
        persistBudget(category, period, "0.05");

        mockMvc.perform(createRequest("0.10", date))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", containsString("tuần " + period.period())))
                .andExpect(flash().attribute("warning", containsString("vượt 0.05.")));
    }

    private MockHttpServletRequestBuilder createRequest(String amount, LocalDate date) {
        MockHttpServletRequestBuilder request = post("/transactions").with(user(owner.getEmail())).with(csrf())
                .param("categoryId", category.getId().toString())
                .param("amount", amount)
                .param("occurredOn", date.toString());
        return request;
    }

    private MockHttpServletRequestBuilder editRequest(Transaction transaction, LocalDate date) {
        MockHttpServletRequestBuilder request = post("/transactions/{id}/edit", transaction.getId())
                .with(user(owner.getEmail())).with(csrf())
                .param("categoryId", transaction.getCategoryId().toString())
                .param("amount", transaction.getAmount().toPlainString())
                .param("occurredOn", date.toString());
        return request;
    }

    private void persistBudget(Category ownedCategory, BudgetPeriod period, String limit) {
        entityManager.persist(new Budget(ownedCategory.getUserId(), ownedCategory.getId(), period, new BigDecimal(limit)));
        entityManager.flush();
    }

    private Transaction persistTransaction(Category ownedCategory, String amount, LocalDate date) {
        Transaction transaction = new Transaction(ownedCategory.getUserId(), ownedCategory.getId(),
                new BigDecimal(amount), date, null);
        entityManager.persist(transaction);
        entityManager.flush();
        return transaction;
    }

    private void assertExpenseTotal(Category ownedCategory, BudgetPeriod period, String expected) {
        BigDecimal total = transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(owner.getId(),
                ownedCategory.getId(), period.startDate(), period.endDate());
        assertThat(total).isEqualByComparingTo(expected);
    }
}
