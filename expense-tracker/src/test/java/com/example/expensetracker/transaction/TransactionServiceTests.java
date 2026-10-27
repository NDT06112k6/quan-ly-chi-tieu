package com.example.expensetracker.transaction;

import com.example.expensetracker.budget.BudgetRepository;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.home.CategoryExpenseDTO;
import com.example.expensetracker.home.DashboardDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTests {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long CATEGORY_ID = 7L;
    private static final Long TRANSACTION_ID = 13L;
    private static final BigDecimal TRANSACTION_AMOUNT = new BigDecimal("12.34");
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 10, 23);
    private static final String TRANSACTION_NOTE = "Lunch";
    private static final String CSV_HEADER = "\uFEFFNgày,Danh mục,Số tiền,Ghi chú\r\n";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private Category category;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void testCreateTransaction_Success() {
        Long userId = 42L;
        Long categoryId = 7L;
        TransactionDTO dto = createTransactionDTO(categoryId);
        when(categoryRepository.findByIdAndUserIdForUpdate(categoryId, userId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(category.getId()).thenReturn(categoryId);

        transactionService.createTransaction(dto, userId);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).saveAndFlush(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(userId, savedTransaction.getUserId());
        assertEquals(categoryId, savedTransaction.getCategoryId());
        assertEquals(dto.getAmount(), savedTransaction.getAmount());
        assertEquals(dto.getOccurredOn(), savedTransaction.getOccurredOn());
        assertEquals(dto.getNote(), savedTransaction.getNote());
        verify(categoryRepository).findByIdAndUserIdForUpdate(categoryId, userId);
    }

    @Test
    void preservesExportDatabaseFailureRatherThanReturningHeaderOnlySuccess() {
        Long userId = 42L;
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(transactionRepository.findExportRowsByUserId(eq(userId), any(Pageable.class))).thenThrow(failure);
        assertThatThrownBy(() -> transactionService.exportTransactionsCsv(userId)).isSameAs(failure);
    }

    @Test
    void testCreateTransaction_CategoryNotFound() {
        Long userId = 42L;
        Long categoryId = 7L;
        TransactionDTO dto = createTransactionDTO(categoryId);
        when(categoryRepository.findByIdAndUserIdForUpdate(categoryId, userId)).thenReturn(Optional.empty());

        assertThrows(InvalidTransactionCategoryException.class, () -> {
            transactionService.createTransaction(dto, userId);
        });

        verify(categoryRepository).findByIdAndUserIdForUpdate(categoryId, userId);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void rejectsAnotherUserReusingTheOwnersCategory() {
        TransactionDTO dto = createTransactionDTO(CATEGORY_ID);
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, OTHER_USER_ID)).thenReturn(Optional.empty());
        when(category.getId()).thenReturn(CATEGORY_ID);

        transactionService.createTransaction(dto, USER_ID);
        assertThrows(InvalidTransactionCategoryException.class, () -> {
            transactionService.createTransaction(dto, OTHER_USER_ID);
        });

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).saveAndFlush(transactionCaptor.capture());
        assertEquals(USER_ID, transactionCaptor.getValue().getUserId());
        verify(categoryRepository).findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID);
        verify(categoryRepository).findByIdAndUserIdForUpdate(CATEGORY_ID, OTHER_USER_ID);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void propagatesTransactionPersistenceFailure() {
        TransactionDTO dto = createTransactionDTO(CATEGORY_ID);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(category.getId()).thenReturn(CATEGORY_ID);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenThrow(failure);

        assertThatThrownBy(() -> transactionService.createTransaction(dto, USER_ID)).isSameAs(failure);

        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
    }

    @Test
    void searchesWithUserScopedFiltersAndMapsTheRequestedPage() {
        TransactionFilterDTO filters = createTransactionFilters();
        Pageable requestedPage = PageRequest.of(1, TransactionService.DEFAULT_PAGE_SIZE, Sort.by("amount"));
        Pageable expectedPage = createSortedPage(1, TransactionService.DEFAULT_PAGE_SIZE);
        Transaction storedTransaction = createStoredTransaction();
        Page<Transaction> storedPage = new PageImpl<>(List.of(storedTransaction), expectedPage, 21);
        when(transactionRepository.searchByUserId(USER_ID, filters, expectedPage)).thenReturn(storedPage);

        Page<TransactionDTO> result = transactionService.getTransactionsByUserId(USER_ID, filters, requestedPage);

        assertThat(result.getContent()).hasSize(1);
        assertEquals(expectedPage, result.getPageable());
        assertEquals(storedPage.getTotalElements(), result.getTotalElements());
        TransactionDTO dto = result.getContent().getFirst();
        assertEquals(TRANSACTION_ID, dto.getId());
        assertEquals(CATEGORY_ID, dto.getCategoryId());
        assertEquals(TRANSACTION_AMOUNT, dto.getAmount());
        assertEquals(TRANSACTION_DATE, dto.getOccurredOn());
        assertEquals(TRANSACTION_NOTE, dto.getNote());
        verify(transactionRepository).searchByUserId(USER_ID, filters, expectedPage);
    }

    @Test
    void capsTransactionPageSizeAndPreservesEmptyResults() {
        TransactionFilterDTO filters = new TransactionFilterDTO();
        Pageable requestedPage = PageRequest.of(1, TransactionService.MAX_PAGE_SIZE + 1);
        Pageable expectedPage = createSortedPage(1, TransactionService.MAX_PAGE_SIZE);
        when(transactionRepository.searchByUserId(USER_ID, filters, expectedPage)).thenReturn(Page.empty(expectedPage));

        Page<TransactionDTO> result = transactionService.getTransactionsByUserId(USER_ID, filters, requestedPage);

        assertThat(result.getContent()).isEmpty();
        assertEquals(expectedPage, result.getPageable());
        verify(transactionRepository).searchByUserId(USER_ID, filters, expectedPage);
    }

    @Test
    void searchesWithoutFiltersWhenOnlyPageAndSizeAreProvided() {
        Pageable expectedPage = createSortedPage(0, TransactionService.DEFAULT_PAGE_SIZE);
        when(transactionRepository.searchByUserId(eq(USER_ID), any(TransactionFilterDTO.class), eq(expectedPage)))
                .thenReturn(Page.empty(expectedPage));

        Page<TransactionDTO> result = transactionService.getTransactionsByUserId(
                USER_ID, 0, TransactionService.DEFAULT_PAGE_SIZE);

        ArgumentCaptor<TransactionFilterDTO> filtersCaptor = ArgumentCaptor.forClass(TransactionFilterDTO.class);
        verify(transactionRepository).searchByUserId(eq(USER_ID), filtersCaptor.capture(), eq(expectedPage));
        assertThat(filtersCaptor.getValue())
                .extracting(TransactionFilterDTO::getKeyword, TransactionFilterDTO::getCategoryId,
                        TransactionFilterDTO::getStartDate, TransactionFilterDTO::getEndDate)
                .containsOnlyNulls();
        assertThat(result.getContent()).isEmpty();
        assertEquals(expectedPage, result.getPageable());
    }

    @Test
    void propagatesSearchFailureRatherThanReturningAnEmptyPage() {
        TransactionFilterDTO filters = new TransactionFilterDTO();
        Pageable pageable = createSortedPage(0, TransactionService.DEFAULT_PAGE_SIZE);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(transactionRepository.searchByUserId(USER_ID, filters, pageable)).thenThrow(failure);

        assertThatThrownBy(() -> transactionService.getTransactionsByUserId(USER_ID, filters, pageable))
                .isSameAs(failure);
    }

    @ParameterizedTest
    @CsvSource({"100, 25.5, 74.50", "10.10, 20.20, -10.10", "0, 0, 0.00"})
    void calculatesUserMonthlyBalanceWithTwoDecimalPlaces(String income, String expense, String balance) {
        YearMonth month = YearMonth.from(TRANSACTION_DATE);
        when(transactionRepository.sumIncomeByUserIdAndMonth(USER_ID, month.getMonthValue(), month.getYear()))
                .thenReturn(new BigDecimal(income));
        when(transactionRepository.sumExpenseByUserIdAndMonth(USER_ID, month.getMonthValue(), month.getYear()))
                .thenReturn(new BigDecimal(expense));

        DashboardDTO dashboard = transactionService.getDashboardData(USER_ID, month.getMonthValue(), month.getYear());

        assertEquals(new BigDecimal(income).setScale(2), dashboard.getTotalIncome());
        assertEquals(new BigDecimal(expense).setScale(2), dashboard.getTotalExpense());
        assertEquals(new BigDecimal(balance), dashboard.getBalance());
        verify(transactionRepository).sumIncomeByUserIdAndMonth(USER_ID, month.getMonthValue(), month.getYear());
        verify(transactionRepository).sumExpenseByUserIdAndMonth(USER_ID, month.getMonthValue(), month.getYear());
    }

    @Test
    void stopsDashboardCalculationWhenIncomeLookupFails() {
        YearMonth month = YearMonth.from(TRANSACTION_DATE);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(transactionRepository.sumIncomeByUserIdAndMonth(USER_ID, month.getMonthValue(), month.getYear()))
                .thenThrow(failure);

        assertThatThrownBy(() -> transactionService.getDashboardData(USER_ID, month.getMonthValue(), month.getYear()))
                .isSameAs(failure);

        verify(transactionRepository).sumIncomeByUserIdAndMonth(USER_ID, month.getMonthValue(), month.getYear());
        verifyNoMoreInteractions(transactionRepository);
    }

    @ParameterizedTest
    @CsvSource({"20, 20", "101, 100"})
    void retrievesUserMonthlyCategoryExpensesWithBoundedPagination(int requestedSize, int expectedSize) {
        YearMonth month = YearMonth.from(TRANSACTION_DATE);
        Pageable requestedPage = PageRequest.of(1, requestedSize);
        Pageable expectedPage = PageRequest.of(1, expectedSize);
        CategoryExpenseDTO expense = new CategoryExpenseDTO("Food", TRANSACTION_AMOUNT);
        Page<CategoryExpenseDTO> expectedExpenses = new PageImpl<>(List.of(expense), expectedPage, 101);
        when(transactionRepository.sumExpensesByCategoryForUserAndMonth(USER_ID, month, expectedPage))
                .thenReturn(expectedExpenses);

        Page<CategoryExpenseDTO> result = transactionService.getExpensesByCategory(USER_ID, month, requestedPage);

        assertSame(expectedExpenses, result);
        verify(transactionRepository).sumExpensesByCategoryForUserAndMonth(USER_ID, month, expectedPage);
    }

    @Test
    void exportsOnlyTheHeaderWhenTheUserHasNoTransactions() {
        Pageable firstPage = PageRequest.of(0, TransactionService.MAX_PAGE_SIZE);
        when(transactionRepository.findExportRowsByUserId(USER_ID, firstPage))
                .thenReturn(new SliceImpl<>(List.of(), firstPage, false));

        String csv = transactionService.exportTransactionsCsv(USER_ID);

        assertEquals(CSV_HEADER, csv);
        verify(transactionRepository).findExportRowsByUserId(USER_ID, firstPage);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void exportsAllPagesForTheSameUserInOrder() {
        Pageable firstPage = PageRequest.of(0, TransactionService.MAX_PAGE_SIZE);
        Pageable secondPage = firstPage.next();
        TransactionExportDTO firstRow = new TransactionExportDTO(TRANSACTION_DATE, "Food", TRANSACTION_AMOUNT, "Lunch");
        TransactionExportDTO secondRow = new TransactionExportDTO(TRANSACTION_DATE, "Travel", BigDecimal.ONE, null);
        when(transactionRepository.findExportRowsByUserId(USER_ID, firstPage))
                .thenReturn(new SliceImpl<>(List.of(firstRow), firstPage, true));
        when(transactionRepository.findExportRowsByUserId(USER_ID, secondPage))
                .thenReturn(new SliceImpl<>(List.of(secondRow), secondPage, false));

        String csv = transactionService.exportTransactionsCsv(USER_ID);

        String expectedCsv = CSV_HEADER + "2026-10-23,Food,12.34,Lunch\r\n2026-10-23,Travel,1,\r\n";
        assertEquals(expectedCsv, csv);
        InOrder repositoryCalls = inOrder(transactionRepository);
        repositoryCalls.verify(transactionRepository).findExportRowsByUserId(USER_ID, firstPage);
        repositoryCalls.verify(transactionRepository).findExportRowsByUserId(USER_ID, secondPage);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void propagatesLaterExportPageFailureRatherThanReturningPartialCsv() {
        Pageable firstPage = PageRequest.of(0, TransactionService.MAX_PAGE_SIZE);
        Pageable secondPage = firstPage.next();
        TransactionExportDTO row = new TransactionExportDTO(TRANSACTION_DATE, "Food", TRANSACTION_AMOUNT, null);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(transactionRepository.findExportRowsByUserId(USER_ID, firstPage))
                .thenReturn(new SliceImpl<>(List.of(row), firstPage, true));
        when(transactionRepository.findExportRowsByUserId(USER_ID, secondPage)).thenThrow(failure);

        assertThatThrownBy(() -> transactionService.exportTransactionsCsv(USER_ID)).isSameAs(failure);

        verify(transactionRepository).findExportRowsByUserId(USER_ID, firstPage);
        verify(transactionRepository).findExportRowsByUserId(USER_ID, secondPage);
        verifyNoMoreInteractions(transactionRepository);
    }

    @ParameterizedTest
    @EnumSource(CategoryType.class)
    void retrievesOwnedTransactionDetailWithTheOwnedCategory(CategoryType categoryType) {
        Transaction storedTransaction = createStoredTransaction();
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(storedTransaction));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(category.getName()).thenReturn("Food");
        when(category.getType()).thenReturn(categoryType);

        TransactionDetailDTO detail = transactionService.getTransactionDetail(TRANSACTION_ID, USER_ID);

        assertEquals(TRANSACTION_ID, detail.id());
        assertEquals(TRANSACTION_AMOUNT, detail.amount());
        assertEquals(TRANSACTION_DATE, detail.occurredOn());
        assertEquals(TRANSACTION_NOTE, detail.note());
        assertEquals("Food", detail.categoryName());
        assertEquals(categoryType, detail.categoryType());
        assertEquals(categoryType == CategoryType.CHI, detail.isExpense());
        verify(transactionRepository).findByIdAndUserId(TRANSACTION_ID, USER_ID);
        verify(categoryRepository).findByIdAndUserId(CATEGORY_ID, USER_ID);
        verifyNoMoreInteractions(transactionRepository, categoryRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {42L, 99L})
    void deniesMissingOrForeignTransactionWithoutReadingCategory(Long userId) {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, userId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> {
            transactionService.getTransactionDetail(TRANSACTION_ID, userId);
        });

        verify(transactionRepository).findByIdAndUserId(TRANSACTION_ID, userId);
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void preservesDetailLookupFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenThrow(failure);

        assertThatThrownBy(() -> transactionService.getTransactionDetail(TRANSACTION_ID, USER_ID)).isSameAs(failure);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void loadsOnlyAnOwnedTransactionForEditing() {
        Transaction storedTransaction = createStoredTransaction();
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(storedTransaction));

        TransactionDTO dto = transactionService.getTransactionForEdit(TRANSACTION_ID, USER_ID);

        assertEquals(TRANSACTION_ID, dto.getId());
        assertEquals(CATEGORY_ID, dto.getCategoryId());
        assertEquals(TRANSACTION_AMOUNT, dto.getAmount());
        assertEquals(TRANSACTION_DATE, dto.getOccurredOn());
        assertEquals(TRANSACTION_NOTE, dto.getNote());
        verify(transactionRepository).findByIdAndUserId(TRANSACTION_ID, USER_ID);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void deniesEditingAForeignTransaction() {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> {
            transactionService.getTransactionForEdit(TRANSACTION_ID, OTHER_USER_ID);
        });

        verify(transactionRepository).findByIdAndUserId(TRANSACTION_ID, OTHER_USER_ID);
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void updatesOnlyTheOwnedTransactionUsingThePathId() {
        TransactionDTO dto = createTransactionDTO(CATEGORY_ID);
        dto.setId(OTHER_USER_ID);
        Transaction original = new Transaction(USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE);
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(original));
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.updateByIdAndUserId(
                TRANSACTION_ID, USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE))
                .thenReturn(1);

        transactionService.updateTransaction(TRANSACTION_ID, dto, USER_ID);

        verify(transactionRepository).findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID);
        verify(transactionRepository).updateByIdAndUserId(
                TRANSACTION_ID, USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void rejectsForeignTransactionBeforeUpdatingOrReadingCategory() {
        TransactionDTO dto = createTransactionDTO(CATEGORY_ID);
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> {
            transactionService.updateTransaction(TRANSACTION_ID, dto, OTHER_USER_ID);
        });

        verify(transactionRepository).findByIdAndUserIdForUpdate(TRANSACTION_ID, OTHER_USER_ID);
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void rejectsUpdatingWithAnotherUsersCategory() {
        TransactionDTO dto = createTransactionDTO(CATEGORY_ID);
        Transaction original = new Transaction(USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE);
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(original));
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidTransactionCategoryException.class, () -> {
            transactionService.updateTransaction(TRANSACTION_ID, dto, USER_ID);
        });

        verify(transactionRepository).findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID);
        verify(categoryRepository).findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void rejectsUpdateWhenTheTransactionDisappearsBeforeTheWrite() {
        TransactionDTO dto = createTransactionDTO(CATEGORY_ID);
        Transaction original = new Transaction(USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE);
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(original));
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.updateByIdAndUserId(
                TRANSACTION_ID, USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE))
                .thenReturn(0);

        assertThrows(AccessDeniedException.class, () -> {
            transactionService.updateTransaction(TRANSACTION_ID, dto, USER_ID);
        });
    }

    @Test
    void deletesOnlyTheTransactionOwnedByTheCurrentUser() {
        Transaction original = new Transaction(USER_ID, CATEGORY_ID, TRANSACTION_AMOUNT, TRANSACTION_DATE, TRANSACTION_NOTE);
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(original));
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.deleteByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(1);

        transactionService.deleteTransaction(TRANSACTION_ID, USER_ID);

        verify(transactionRepository).findByIdAndUserIdForUpdate(TRANSACTION_ID, USER_ID);
        verify(transactionRepository).deleteByIdAndUserId(TRANSACTION_ID, USER_ID);
        verifyNoMoreInteractions(transactionRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {42L, 99L})
    void deniesDeletingMissingOrForeignTransactions(Long userId) {
        when(transactionRepository.findByIdAndUserIdForUpdate(TRANSACTION_ID, userId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> {
            transactionService.deleteTransaction(TRANSACTION_ID, userId);
        });

        verify(transactionRepository).findByIdAndUserIdForUpdate(TRANSACTION_ID, userId);
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(categoryRepository, budgetRepository);
    }

    private Pageable createSortedPage(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "occurredOn", "id");
        Pageable pageable = PageRequest.of(page, size, sort);
        return pageable;
    }

    private TransactionFilterDTO createTransactionFilters() {
        TransactionFilterDTO filters = new TransactionFilterDTO();
        filters.setKeyword(TRANSACTION_NOTE);
        filters.setCategoryId(CATEGORY_ID);
        filters.setStartDate(TRANSACTION_DATE.minusDays(1));
        filters.setEndDate(TRANSACTION_DATE);
        return filters;
    }

    private Transaction createStoredTransaction() {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(TRANSACTION_ID);
        when(transaction.getCategoryId()).thenReturn(CATEGORY_ID);
        when(transaction.getAmount()).thenReturn(TRANSACTION_AMOUNT);
        when(transaction.getOccurredOn()).thenReturn(TRANSACTION_DATE);
        when(transaction.getNote()).thenReturn(TRANSACTION_NOTE);
        return transaction;
    }

    private TransactionDTO createTransactionDTO(Long categoryId) {
        TransactionDTO dto = new TransactionDTO();
        dto.setAmount(TRANSACTION_AMOUNT);
        dto.setOccurredOn(TRANSACTION_DATE);
        dto.setNote(TRANSACTION_NOTE);
        dto.setCategoryId(categoryId);
        return dto;
    }
}
