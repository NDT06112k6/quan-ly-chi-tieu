package com.example.expensetracker.home;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.transaction.Transaction;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardChartIntegrationTests {

    private final MockMvc mockMvc;
    private final TransactionService transactionService;
    private final EntityManager entityManager;
    private User owner;
    private Category expenseCategory;
    private Category incomeCategory;
    private Category foreignCategory;

    @Autowired
    DashboardChartIntegrationTests(MockMvc mockMvc, TransactionService transactionService, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.transactionService = transactionService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = new User("chart-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        User otherUser = new User("chart-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(otherUser);
        expenseCategory = persistCategory(owner, "Ăn uống", CategoryType.CHI);
        incomeCategory = persistCategory(owner, "Lương", CategoryType.THU);
        foreignCategory = persistCategory(otherUser, "Ăn uống", CategoryType.CHI);
    }

    @Test
    void apiAggregatesOnlyCurrentOwnersExpensesInCurrentMonth() throws Exception {
        YearMonth month = YearMonth.now();
        persistTransaction(expenseCategory, "0.10", month.atDay(1));
        persistTransaction(expenseCategory, "0.20", month.atEndOfMonth());
        persistTransaction(expenseCategory, "999.00", month.atDay(1).minusDays(1));
        persistTransaction(expenseCategory, "888.00", month.atEndOfMonth().plusDays(1));
        persistTransaction(expenseCategory, "777.00", month.minusYears(1).atDay(1));
        persistTransaction(incomeCategory, "1000.00", month.atDay(1));
        persistTransaction(foreignCategory, "9000.00", month.atDay(1));
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user(owner.getEmail()))
                        .param("userId", foreignCategory.getUserId().toString())
                        .param("month", "1").param("year", "2000"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("Ăn uống"))
                .andExpect(content().string(containsString("\"totalAmount\":0.30")))
                .andExpect(header().string("X-Has-Next", "false"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void returnsEmptyArrayOnlyWhenOwnerHasNoExpenses() throws Exception {
        LocalDate today = LocalDate.now();
        persistTransaction(incomeCategory, "1000.00", today);
        persistTransaction(foreignCategory, "9000.00", today);
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"))
                .andExpect(header().string("X-Has-Next", "false"));
    }

    @Test
    void groupsByCategoryIdentityEvenWhenNamesAreIdenticalAndPaginatesGroups() throws Exception {
        Category sameNameCategory = persistCategory(owner, expenseCategory.getName(), CategoryType.CHI);
        persistTransaction(expenseCategory, "1.00", LocalDate.now());
        persistTransaction(expenseCategory, "2.00", LocalDate.now());
        persistTransaction(sameNameCategory, "4.00", LocalDate.now());
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user(owner.getEmail())).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(3))
                .andExpect(header().string("X-Has-Next", "true"));
        mockMvc.perform(get(DashboardApiController.CHART_PATH).with(user(owner.getEmail()))
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(4))
                .andExpect(header().string("X-Has-Next", "false"));
    }

    @Test
    void capsRepositoryPageAndDoesNotDropCategoriesAfterFirstHundred() {
        for (int index = 0; index < 101; index++) {
            Category category = persistCategory(owner, "Expense " + index, CategoryType.CHI);
            persistTransaction(category, "1.00", LocalDate.now());
        }
        Page<CategoryExpenseDTO> firstPage = transactionService.getExpensesByCategory(
                owner.getId(), YearMonth.now(), PageRequest.of(0, 1000));
        Page<CategoryExpenseDTO> lastPage = transactionService.getExpensesByCategory(
                owner.getId(), YearMonth.now(), PageRequest.of(1, 100));
        assertThat(firstPage.getContent()).hasSize(100);
        assertThat(firstPage.getTotalElements()).isEqualTo(101);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.getContent()).hasSize(1);
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void preservesExactLargeAggregatedAmounts() {
        persistTransaction(expenseCategory, "9999999999999.99", LocalDate.now());
        persistTransaction(expenseCategory, "9999999999999.99", LocalDate.now());
        Page<CategoryExpenseDTO> expenses = transactionService.getExpensesByCategory(
                owner.getId(), YearMonth.now(), PageRequest.of(0, 10));
        assertThat(expenses.getContent().getFirst().totalAmount())
                .isEqualTo(new BigDecimal("19999999999999.98"));
    }

    @ParameterizedTest
    @MethodSource("boundaryMonths")
    void includesWholeMonthAndExcludesAdjacentDates(YearMonth month) {
        persistTransaction(expenseCategory, "0.10", month.atDay(1));
        persistTransaction(expenseCategory, "0.20", month.atEndOfMonth());
        persistTransaction(expenseCategory, "999.00", month.atDay(1).minusDays(1));
        persistTransaction(expenseCategory, "999.00", month.atEndOfMonth().plusDays(1));
        Page<CategoryExpenseDTO> expenses = transactionService.getExpensesByCategory(
                owner.getId(), month, PageRequest.of(0, 10));
        assertThat(expenses.getContent()).hasSize(1);
        assertThat(expenses.getContent().getFirst().totalAmount()).isEqualTo(new BigDecimal("0.30"));
    }

    @ParameterizedTest
    @MethodSource("boundaryMonths")
    void paginatedGroupCountUsesTheSameDateRangeAndOwnerAsTheAmounts(YearMonth month) {
        Category secondCategory = persistCategory(owner, "Transport", CategoryType.CHI);
        Category outsideCategory = persistCategory(owner, "Outside period", CategoryType.CHI);
        persistTransaction(expenseCategory, "1.00", month.atDay(1));
        persistTransaction(secondCategory, "2.00", month.atEndOfMonth());
        persistTransaction(outsideCategory, "999.00", month.atDay(1).minusDays(1));
        persistTransaction(foreignCategory, "999.00", month.atDay(1));

        Page<CategoryExpenseDTO> firstPage = transactionService.getExpensesByCategory(
                owner.getId(), month, PageRequest.of(0, 1));
        Page<CategoryExpenseDTO> secondPage = transactionService.getExpensesByCategory(
                owner.getId(), month, PageRequest.of(1, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent().getFirst().categoryName()).isEqualTo(expenseCategory.getName());
        assertThat(firstPage.getContent().getFirst().totalAmount()).isEqualByComparingTo("1.00");
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getContent().getFirst().categoryName()).isEqualTo("Transport");
        assertThat(secondPage.getContent().getFirst().totalAmount()).isEqualByComparingTo("2.00");
    }

    private Category persistCategory(User categoryOwner, String name, CategoryType type) {
        Category category = new Category(categoryOwner.getId(), name, type);
        entityManager.persist(category);
        return category;
    }

    private void persistTransaction(Category category, String amount, LocalDate date) {
        Transaction transaction = new Transaction(category.getUserId(), category.getId(), new BigDecimal(amount), date, null);
        entityManager.persist(transaction);
        entityManager.flush();
    }

    private static Stream<YearMonth> boundaryMonths() {
        Stream<YearMonth> months = Stream.of(YearMonth.of(2024, 2), YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        return months;
    }
}
