package com.example.expensetracker.transaction;

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
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionBudgetAlertServiceTests {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;
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
    void returnsExactExceededAmountAfterSavingExpenseAndUsesOnlyOwnedCurrentMonthBudget() {
        YearMonth month = YearMonth.now();
        configureCategory(CategoryType.CHI);
        configureBudget(month);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month))
                .thenReturn(new BigDecimal("100.10"));
        TransactionDTO dto = transactionDto();

        Optional<String> warning = transactionService.createTransaction(dto, USER_ID);

        assertThat(warning).hasValue("Danh mục \"Food\" đã vượt ngân sách tháng " + month
                + ": tổng chi 100.10, hạn mức 100.00, vượt 0.10.");
        InOrder writesAndChecks = inOrder(categoryRepository, transactionRepository, budgetRepository);
        writesAndChecks.verify(categoryRepository).findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID);
        writesAndChecks.verify(transactionRepository).saveAndFlush(any(Transaction.class));
        writesAndChecks.verify(budgetRepository).findByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month.atDay(1));
        writesAndChecks.verify(transactionRepository).sumExpensesByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "99.99", "100.00"})
    void doesNotWarnWhenExpenseTotalIsAtOrBelowLimit(String total) {
        YearMonth month = YearMonth.now();
        configureCategory(CategoryType.CHI);
        configureBudget(month);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month))
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
        verify(budgetRepository).findByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, YearMonth.now().atDay(1));
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
        configureCategory(CategoryType.CHI);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Insert failed");
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID)).isSameAs(failure);

        verifyNoInteractions(budgetRepository);
    }

    @Test
    void preservesBudgetLookupFailureInsteadOfReturningNoWarning() {
        YearMonth month = YearMonth.now();
        configureCategory(CategoryType.CHI);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Budget lookup failed");
        when(budgetRepository.findByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month.atDay(1)))
                .thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID)).isSameAs(failure);
    }

    @Test
    void preservesExpenseTotalFailureInsteadOfReturningNoWarning() {
        YearMonth month = YearMonth.now();
        configureCategory(CategoryType.CHI);
        configureBudget(month);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Expense query failed");
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month))
                .thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionDto(), USER_ID)).isSameAs(failure);
    }

    @Test
    void checksCurrentMonthEvenWhenSubmittedTransactionDateIsInAnotherMonth() {
        YearMonth currentMonth = YearMonth.now();
        configureCategory(CategoryType.CHI);
        configureBudget(currentMonth);
        when(transactionRepository.sumExpensesByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, currentMonth))
                .thenReturn(new BigDecimal("110.00"));
        TransactionDTO dto = transactionDto();
        dto.setOccurredOn(currentMonth.minusMonths(1).atDay(1));

        Optional<String> warning = transactionService.createTransaction(dto, USER_ID);

        assertThat(warning).isPresent();
        verify(budgetRepository).findByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, currentMonth.atDay(1));
    }

    private void configureCategory(CategoryType type) {
        Category category = new Category(USER_ID, "Food", type);
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
    }

    private void configureBudget(YearMonth month) {
        Budget budget = new Budget(USER_ID, CATEGORY_ID, month.atDay(1), LIMIT);
        when(budgetRepository.findByUserIdAndCategoryIdAndMonth(USER_ID, CATEGORY_ID, month.atDay(1)))
                .thenReturn(Optional.of(budget));
    }

    private TransactionDTO transactionDto() {
        TransactionDTO dto = new TransactionDTO();
        dto.setCategoryId(CATEGORY_ID);
        dto.setAmount(new BigDecimal("0.10"));
        dto.setOccurredOn(YearMonth.now().atDay(1));
        return dto;
    }
}
