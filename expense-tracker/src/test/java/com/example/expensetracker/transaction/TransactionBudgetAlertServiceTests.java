package com.example.expensetracker.transaction;

import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.budget.BudgetRepository;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionBudgetAlertServiceTests {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;
    private static final Long TRANSACTION_ID = 13L;
    private static final YearMonth TRANSACTION_MONTH = YearMonth.of(2024, 2);
    private static final BigDecimal LIMIT = new BigDecimal("100.00");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void returnsExactExceededAmountAfterSavingExpenseAndUsesOwnedTransactionMonthBudget() {
        YearMonth month = TRANSACTION_MONTH;
        configureCategory(CategoryType.CHI);
        configureBudget(month);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(new BigDecimal("100.10"));
        TransactionDTO dto = transactionDto();

        Optional<String> warning = transactionService.createTransaction(dto, USER_ID);

        assertThat(warning).hasValue("Danh mục \"Food\" đã vượt ngân sách tháng " + month
                + ": tổng chi 100.10, hạn mức 100.00, vượt 0.10.");
        InOrder writesAndChecks = inOrder(categoryRepository, transactionRepository, budgetRepository);
        writesAndChecks.verify(categoryRepository).findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID);
        writesAndChecks.verify(transactionRepository).saveAndFlush(any(Transaction.class));
        writesAndChecks.verify(budgetRepository).findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, CATEGORY_ID, month.toString(), BudgetType.MONTHLY);
        writesAndChecks.verify(transactionRepository).sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, month.atDay(1), month.atEndOfMonth());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "99.99", "100.00"})
    void doesNotWarnWhenExpenseTotalIsAtOrBelowLimit(String total) {
        YearMonth month = TRANSACTION_MONTH;
        configureCategory(CategoryType.CHI);
        configureBudget(month);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(new BigDecimal(total));

        Optional<String> warning = transactionService.createTransaction(transactionDto(), USER_ID);

        assertThat(warning).isEmpty();
        verify(transactionRepository).saveAndFlush(any(Transaction.class));
    }

    @Test
    void savesExpenseWithoutWarningWhenNoOwnedBudgetExists() {
        configureCategory(CategoryType.CHI);

        Optional<String> warning = transactionService.createTransaction(transactionDto(), USER_ID);

        assertThat(warning).isEmpty();
        verify(budgetRepository).findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, CATEGORY_ID, TRANSACTION_MONTH.toString(), BudgetType.MONTHLY);
        verify(transactionRepository).saveAndFlush(any(Transaction.class));
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void savesIncomeWithoutCheckingExpenseBudget() {
        configureCategory(CategoryType.THU);

        Optional<String> warning = transactionService.createTransaction(transactionDto(), USER_ID);

        assertThat(warning).isEmpty();
        verify(transactionRepository).saveAndFlush(any(Transaction.class));
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(budgetRepository);
    }

    @Test
    void deniesForeignCategoryBeforeSavingOrLookingUpBudget() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID))
                .isInstanceOf(InvalidTransactionCategoryException.class);

        verifyNoInteractions(transactionRepository, budgetRepository);
    }

    @Test
    void doesNotCheckBudgetWhenTransactionCannotBeSaved() {
        configureLockedCategory(CategoryType.CHI);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Insert failed");
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID)).isSameAs(failure);

        verifyNoInteractions(budgetRepository);
    }

    @Test
    void preservesBudgetLookupFailureInsteadOfReturningNoWarning() {
        YearMonth month = TRANSACTION_MONTH;
        configureCategory(CategoryType.CHI);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Budget lookup failed");
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, CATEGORY_ID, month.toString(), BudgetType.MONTHLY))
                .thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID)).isSameAs(failure);
    }

    @Test
    void preservesExpenseTotalFailureInsteadOfReturningNoWarning() {
        YearMonth month = TRANSACTION_MONTH;
        configureCategory(CategoryType.CHI);
        configureBudget(month);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Expense query failed");
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, month.atDay(1), month.atEndOfMonth()))
                .thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID)).isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-12", "2024-02", "2031-01", "9999-12"})
    void usesSubmittedTransactionMonthAndYearInsteadOfServerDate(String value) {
        YearMonth transactionMonth = YearMonth.parse(value);
        configureCategory(CategoryType.CHI);
        configureBudget(transactionMonth);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, transactionMonth.atDay(1), transactionMonth.atEndOfMonth()))
                .thenReturn(new BigDecimal("110.00"));
        TransactionDTO dto = transactionDto();
        dto.setOccurredOn(transactionMonth.atEndOfMonth());

        Optional<String> warning = transactionService.createTransaction(dto, USER_ID);

        assertThat(warning).isPresent();
        verify(budgetRepository).findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, CATEGORY_ID, transactionMonth.toString(), BudgetType.MONTHLY);
        assertThat(warning.orElseThrow()).contains(transactionMonth.toString());
    }

    @Test
    void checksUpdatedTotalOnceWhenEditStaysInSameCategoryAndMonth() {
        configureStoredTransaction();
        configureCategory(CategoryType.CHI);
        configureBudget(TRANSACTION_MONTH);
        configureSuccessfulUpdate(CATEGORY_ID);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal("100.10"));
        TransactionDTO dto = transactionDto();
        dto.setOccurredOn(TRANSACTION_MONTH.atEndOfMonth());

        Optional<String> warning = transactionService.updateTransaction(TRANSACTION_ID, dto, USER_ID);

        assertThat(warning.orElseThrow()).contains("2024-02", "vượt 0.10.");
        verify(transactionRepository, times(1)).sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth());
    }

    @ParameterizedTest
    @ValueSource(strings = {"99.99", "100.00"})
    void clearsWarningWhenUpdatedTotalIsAtOrBelowLimit(String total) {
        configureStoredTransaction();
        configureCategory(CategoryType.CHI);
        configureBudget(TRANSACTION_MONTH);
        configureSuccessfulUpdate(CATEGORY_ID);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal(total));

        Optional<String> warning = transactionService.updateTransaction(TRANSACTION_ID, transactionDto(), USER_ID);

        assertThat(warning).isEmpty();
    }

    @Test
    void checksBothOldAndNewMonthsWhenTransactionDateChanges() {
        YearMonth updatedMonth = TRANSACTION_MONTH.plusMonths(1);
        configureStoredTransaction();
        configureCategory(CategoryType.CHI);
        configureBudget(TRANSACTION_MONTH);
        configureBudget(updatedMonth);
        configureMissingWeeklyBudget(CATEGORY_ID, TRANSACTION_MONTH);
        configureMissingWeeklyBudget(CATEGORY_ID, updatedMonth);
        configureSuccessfulUpdate(CATEGORY_ID);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal("110.00"));
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, updatedMonth.atDay(1), updatedMonth.atEndOfMonth()))
                .thenReturn(new BigDecimal("130.00"));
        TransactionDTO dto = transactionDto();
        dto.setOccurredOn(updatedMonth.atDay(1));

        Optional<String> warning = transactionService.updateTransaction(TRANSACTION_ID, dto, USER_ID);

        assertThat(warning.orElseThrow()).contains("2024-02", "2024-03", "vượt 10.00.", "vượt 30.00.");
    }

    @ParameterizedTest
    @ValueSource(longs = {3L, 11L})
    void checksBothCategoriesAndLocksInIdOrderWhenCategoryChanges(Long updatedCategoryId) {
        configureStoredTransaction();
        configureCategory(CategoryType.CHI);
        configureBudget(TRANSACTION_MONTH);
        configureOtherCategoryBudget(updatedCategoryId);
        configureMissingWeeklyBudget(CATEGORY_ID, TRANSACTION_MONTH);
        configureMissingWeeklyBudget(updatedCategoryId, TRANSACTION_MONTH);
        configureSuccessfulUpdate(updatedCategoryId);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal("110.00"));
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, updatedCategoryId, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal("120.00"));
        TransactionDTO dto = transactionDto();
        dto.setCategoryId(updatedCategoryId);

        Optional<String> warning = transactionService.updateTransaction(TRANSACTION_ID, dto, USER_ID);

        assertThat(warning.orElseThrow()).contains("Food", "Other expense", "vượt 10.00.", "vượt 20.00.");
        InOrder categoryLocks = inOrder(categoryRepository);
        Long firstId = Math.min(CATEGORY_ID, updatedCategoryId);
        Long secondId = Math.max(CATEGORY_ID, updatedCategoryId);
        categoryLocks.verify(categoryRepository).findByIdAndUserIdForUpdate(firstId, USER_ID);
        categoryLocks.verify(categoryRepository).findByIdAndUserIdForUpdate(secondId, USER_ID);
    }

    @Test
    void checksRemainingTotalAfterDeletingUsingOriginalCategoryAndDate() {
        configureStoredTransaction();
        configureCategory(CategoryType.CHI);
        configureBudget(TRANSACTION_MONTH);
        when(transactionRepository.deleteByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(1);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal("110.00"));

        Optional<String> warning = transactionService.deleteTransaction(TRANSACTION_ID, USER_ID);

        assertThat(warning.orElseThrow()).contains("2024-02", "vượt 10.00.");
        InOrder deletionAndCheck = inOrder(transactionRepository);
        deletionAndCheck.verify(transactionRepository).findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID);
        deletionAndCheck.verify(transactionRepository).deleteByIdAndUserId(TRANSACTION_ID, USER_ID);
        deletionAndCheck.verify(transactionRepository).sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "99.99", "100.00"})
    void doesNotWarnWhenRemainingTotalAfterDeletionIsAtOrBelowLimit(String total) {
        configureStoredTransaction();
        configureCategory(CategoryType.CHI);
        configureBudget(TRANSACTION_MONTH);
        when(transactionRepository.deleteByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(1);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                USER_ID, CATEGORY_ID, TRANSACTION_MONTH.atDay(1), TRANSACTION_MONTH.atEndOfMonth()))
                .thenReturn(new BigDecimal(total));

        Optional<String> warning = transactionService.deleteTransaction(TRANSACTION_ID, USER_ID);

        assertThat(warning).isEmpty();
    }

    private void configureCategory(CategoryType type) {
        Category category = configureLockedCategory(type);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
    }

    private Category configureLockedCategory(CategoryType type) {
        Category category = new Category(USER_ID, "Food", type);
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        return category;
    }

    private void configureOtherCategoryBudget(Long categoryId) {
        Category category = new Category(USER_ID, "Other expense", CategoryType.CHI);
        ReflectionTestUtils.setField(category, "id", categoryId);
        when(categoryRepository.findByIdAndUserIdForUpdate(categoryId, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).thenReturn(Optional.of(category));
        Budget budget = new Budget(
                USER_ID,
                categoryId,
                BudgetPeriod.containing(BudgetType.MONTHLY, TRANSACTION_MONTH.atDay(1)),
                LIMIT);
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, categoryId, TRANSACTION_MONTH.toString(), BudgetType.MONTHLY))
                .thenReturn(Optional.of(budget));
    }

    private void configureStoredTransaction() {
        Transaction transaction = new Transaction(USER_ID, CATEGORY_ID, new BigDecimal("0.10"),
                TRANSACTION_MONTH.atDay(1), null);
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(transaction));
    }

    private void configureSuccessfulUpdate(Long categoryId) {
        when(transactionRepository.updateByIdAndUserId(eq(TRANSACTION_ID), eq(USER_ID), eq(categoryId),
                any(BigDecimal.class), any(LocalDate.class), any())).thenReturn(1);
    }

    private void configureBudget(YearMonth month) {
        Budget budget = new Budget(
                USER_ID,
                CATEGORY_ID,
                BudgetPeriod.containing(BudgetType.MONTHLY, month.atDay(1)),
                LIMIT);
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, CATEGORY_ID, month.toString(), BudgetType.MONTHLY))
                .thenReturn(Optional.of(budget));
    }

    private void configureMissingWeeklyBudget(Long categoryId, YearMonth month) {
        BudgetPeriod period = BudgetPeriod.containing(BudgetType.WEEKLY, month.atDay(1));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriodAndType(
                USER_ID, categoryId, period.period(), BudgetType.WEEKLY)).thenReturn(Optional.empty());
    }

    private TransactionDTO transactionDto() {
        TransactionDTO dto = new TransactionDTO();
        dto.setCategoryId(CATEGORY_ID);
        dto.setAmount(new BigDecimal("0.10"));
        dto.setOccurredOn(TRANSACTION_MONTH.atDay(1));
        return dto;
    }
}
