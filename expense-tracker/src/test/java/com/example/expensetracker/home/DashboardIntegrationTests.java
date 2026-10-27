package com.example.expensetracker.home;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.transaction.Transaction;
import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTests {

    private final MockMvc mockMvc;
    private final TransactionService transactionService;
    private final EntityManager entityManager;

    private User owner;
    private User otherUser;
    private Category incomeCategory;
    private Category expenseCategory;
    private Category foreignIncomeCategory;
    private Category foreignExpenseCategory;

    @Autowired
    DashboardIntegrationTests(MockMvc mockMvc, TransactionService transactionService, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.transactionService = transactionService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = new User("dashboard-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        otherUser = new User("dashboard-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(otherUser);
        incomeCategory = persistCategory(owner, CategoryType.THU);
        expenseCategory = persistCategory(owner, CategoryType.CHI);
        foreignIncomeCategory = persistCategory(otherUser, CategoryType.THU);
        foreignExpenseCategory = persistCategory(otherUser, CategoryType.CHI);
        entityManager.flush();
    }

    @Test
    void sumsOnlyRequestedMonthYearAndOwnerWithExactDecimalBalance() {
        persistTransaction(incomeCategory, "2000.10", LocalDate.of(2026, 10, 1));
        persistTransaction(incomeCategory, "0.20", LocalDate.of(2026, 10, 31));
        persistTransaction(expenseCategory, "450.05", LocalDate.of(2026, 10, 15));
        persistTransaction(expenseCategory, "49.95", LocalDate.of(2026, 10, 31));
        persistTransaction(incomeCategory, "9999.00", LocalDate.of(2026, 9, 30));
        persistTransaction(incomeCategory, "8888.00", LocalDate.of(2025, 10, 1));
        persistTransaction(expenseCategory, "7777.00", LocalDate.of(2026, 11, 1));
        persistTransaction(foreignIncomeCategory, "900000.00", LocalDate.of(2026, 10, 15));
        persistTransaction(foreignExpenseCategory, "800000.00", LocalDate.of(2026, 10, 15));

        DashboardDTO dashboard = transactionService.getDashboardData(owner.getId(), 10, 2026);

        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("2000.30");
        assertThat(dashboard.getTotalExpense()).isEqualByComparingTo("500.00");
        assertThat(dashboard.getBalance()).isEqualByComparingTo("1500.30");
    }

    @Test
    void returnsZeroForEmptyMonthWithoutIncludingOtherUsersTransactions() {
        persistTransaction(foreignIncomeCategory, "100.00", LocalDate.of(2026, 10, 1));
        persistTransaction(foreignExpenseCategory, "20.00", LocalDate.of(2026, 10, 1));

        DashboardDTO dashboard = transactionService.getDashboardData(owner.getId(), 10, 2026);

        assertThat(dashboard.getTotalIncome()).isEqualTo(new BigDecimal("0.00"));
        assertThat(dashboard.getTotalExpense()).isEqualTo(new BigDecimal("0.00"));
        assertThat(dashboard.getBalance()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void calculatesNegativeBalanceForExpenseOnlyMonth() {
        persistTransaction(expenseCategory, "0.10", LocalDate.of(2026, 10, 1));
        persistTransaction(expenseCategory, "0.20", LocalDate.of(2026, 10, 2));

        DashboardDTO dashboard = transactionService.getDashboardData(owner.getId(), 10, 2026);

        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("0.00");
        assertThat(dashboard.getTotalExpense()).isEqualByComparingTo("0.30");
        assertThat(dashboard.getBalance()).isEqualByComparingTo("-0.30");
    }

    @Test
    void preservesLargeAggregatesBeyondSingleTransactionColumnPrecision() {
        persistTransaction(incomeCategory, "9999999999999.99", LocalDate.of(2026, 10, 1));
        persistTransaction(incomeCategory, "9999999999999.99", LocalDate.of(2026, 10, 2));
        persistTransaction(expenseCategory, "0.01", LocalDate.of(2026, 10, 2));

        DashboardDTO dashboard = transactionService.getDashboardData(owner.getId(), 10, 2026);

        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("19999999999999.98");
        assertThat(dashboard.getBalance()).isEqualByComparingTo("19999999999999.97");
    }

    @ParameterizedTest
    @MethodSource("boundaryMonths")
    void includesWholeCalendarMonthAndExcludesAdjacentDates(YearMonth month) {
        persistTransaction(incomeCategory, "1.01", month.atDay(1));
        persistTransaction(incomeCategory, "1.01", month.atEndOfMonth());
        persistTransaction(incomeCategory, "999.00", month.atDay(1).minusDays(1));
        persistTransaction(incomeCategory, "999.00", month.atEndOfMonth().plusDays(1));

        DashboardDTO dashboard = transactionService.getDashboardData(
                owner.getId(), month.getMonthValue(), month.getYear());

        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("2.02");
        assertThat(dashboard.getTotalExpense()).isEqualByComparingTo("0.00");
        assertThat(dashboard.getBalance()).isEqualByComparingTo("2.02");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000-01", "9999-12"})
    void aggregatesDashboardAndChartAtSupportedDateRangeLimits(String monthValue) {
        YearMonth month = YearMonth.parse(monthValue);
        persistTransaction(incomeCategory, "1.01", month.atDay(1));
        persistTransaction(incomeCategory, "1.01", month.atEndOfMonth());
        persistTransaction(expenseCategory, "0.10", month.atEndOfMonth());
        persistTransaction(foreignIncomeCategory, "999.00", month.atDay(1));
        persistTransaction(foreignExpenseCategory, "999.00", month.atEndOfMonth());

        DashboardDTO dashboard = transactionService.getDashboardData(owner.getId(), month.getMonthValue(), month.getYear());
        Page<CategoryExpenseDTO> chart = transactionService.getExpensesByCategory(owner.getId(), month, PageRequest.of(0, 20));

        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("2.02");
        assertThat(dashboard.getTotalExpense()).isEqualByComparingTo("0.10");
        assertThat(dashboard.getBalance()).isEqualByComparingTo("1.92");
        assertThat(chart.getContent()).hasSize(1);
        assertThat(chart.getContent().getFirst().totalAmount()).isEqualByComparingTo("0.10");
    }

    @ParameterizedTest
    @MethodSource("invalidPeriods")
    void rejectsInvalidMonthAndYear(int month, int year) {
        assertThatThrownBy(() -> transactionService.getDashboardData(owner.getId(), month, year))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void homeUsesCurrentMonthAndAuthenticatedOwnerRatherThanRequestParameters() throws Exception {
        LocalDate currentDate = LocalDate.now();
        LocalDate monthStart = currentDate.withDayOfMonth(1);
        persistTransaction(incomeCategory, "100.10", monthStart);
        persistTransaction(expenseCategory, "20.05", monthStart);
        persistTransaction(incomeCategory, "8888.00", monthStart.minusDays(1));
        persistTransaction(foreignIncomeCategory, "9999.00", monthStart);

        MvcResult result = mockMvc.perform(get("/").with(user(owner.getEmail()))
                        .param("userId", otherUser.getId().toString()).param("month", "1").param("year", "2000"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("currentMonth", currentDate.getMonthValue()))
                .andExpect(model().attribute("currentYear", currentDate.getYear()))
                .andExpect(content().string(containsString("Tổng thu")))
                .andExpect(content().string(containsString("Tổng chi")))
                .andExpect(content().string(containsString("Số dư")))
                .andExpect(content().string(containsString("href=\"/transactions/new\"")))
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        DashboardDTO dashboard = (DashboardDTO) result.getModelAndView().getModel().get("dashboard");
        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("100.10");
        assertThat(dashboard.getTotalExpense()).isEqualByComparingTo("20.05");
        assertThat(dashboard.getBalance()).isEqualByComparingTo("80.05");
    }

    private Category persistCategory(User user, CategoryType type) {
        Category category = new Category(user.getId(), type.name(), type);
        entityManager.persist(category);
        return category;
    }

    private void persistTransaction(Category category, String amount, LocalDate occurredOn) {
        Transaction transaction = new Transaction(category.getUserId(), category.getId(),
                new BigDecimal(amount), occurredOn, null);
        entityManager.persist(transaction);
        entityManager.flush();
    }

    private static Stream<YearMonth> boundaryMonths() {
        Stream<YearMonth> months = Stream.of(
                YearMonth.of(2024, 2), YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        return months;
    }

    private static Stream<Arguments> invalidPeriods() {
        Stream<Arguments> periods = Stream.of(
                Arguments.of(0, 2026), Arguments.of(13, 2026),
                Arguments.of(10, 0), Arguments.of(10, 10000));
        return periods;
    }
}
