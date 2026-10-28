package com.example.expensetracker.transaction;

import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetRepository;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.home.DashboardDTO;
import com.example.expensetracker.home.CategoryExpenseDTO;
import com.example.expensetracker.common.util.CsvUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Validated
@Transactional(readOnly = true)
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String DATE_SORT_PROPERTY = "occurredOn";
    private static final String ID_SORT_PROPERTY = "id";
    private static final int MONEY_SCALE = 2;
    private static final LocalDate MINIMUM_TRANSACTION_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_TRANSACTION_DATE = LocalDate.of(9999, 12, 31);
    private static final int EXPECTED_WRITE_COUNT = 1;
    private static final String TRANSACTION_ACCESS_DENIED = "Không thể truy cập giao dịch này.";
    private static final String UTF8_BOM = "\uFEFF";
    private static final String BUDGET_WARNING_MESSAGE =
            "Danh mục \"%s\" đã vượt ngân sách %s %s: tổng chi %s, hạn mức %s, vượt %s.";
    private static final List<String> CSV_HEADER = List.of("Ngày", "Danh mục", "Số tiền", "Ghi chú");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository,
            BudgetRepository budgetRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.budgetRepository = budgetRepository;
    }

    @Transactional
    public Optional<String> createTransaction(@Valid @NotNull TransactionDTO dto, @NotNull @Positive Long userId) {
        // Serialize category writes so each budget check sees earlier committed transactions.
        Category category = findOwnedCategoryForUpdate(dto.getCategoryId(), userId);

        Transaction transaction = new Transaction(
                userId, category.getId(), dto.getAmount(), dto.getOccurredOn(), dto.getNote());
        transactionRepository.saveAndFlush(transaction);
        Optional<String> warning = checkBudgetAlert(userId, category.getId(), dto.getOccurredOn());
        return warning;
    }

    private Optional<String> checkBudgetAlert(Long userId, Long categoryId, LocalDate occurredOn) {
        Category category = findOwnedCategory(categoryId, userId);
        Optional<String> warning = Optional.empty();
        for (BudgetType type : BudgetType.values()) {
            BudgetPeriod period = BudgetPeriod.containing(type, occurredOn);
            Optional<String> periodWarning = checkBudgetAlertForPeriod(userId, category, period);
            warning = combineWarnings(warning, periodWarning);
        }
        return warning;
    }

    private Optional<String> checkBudgetAlertForPeriod(Long userId, Category category, BudgetPeriod period) {
        Optional<String> warning = Optional.empty();
        if (category.getType() != CategoryType.CHI) {
            return warning;
        }
        Optional<Budget> budget = budgetRepository.findByUserIdAndCategoryIdAndPeriodAndType(
                userId, category.getId(), period.period(), period.type());
        if (budget.isEmpty()) {
            return warning;
        }
        BigDecimal totalExpense = sumBudgetExpenses(userId, category.getId(), period);
        warning = createBudgetWarning(category, budget.get(), totalExpense);
        return warning;
    }

    private BigDecimal sumBudgetExpenses(Long userId, Long categoryId, BudgetPeriod period) {
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();
        // ISO weeks can cross the storage boundary; no transactions can exist outside MySQL's DATE range.
        if (startDate.isBefore(MINIMUM_TRANSACTION_DATE)) {
            startDate = MINIMUM_TRANSACTION_DATE;
        }
        if (endDate.isAfter(MAXIMUM_TRANSACTION_DATE)) {
            endDate = MAXIMUM_TRANSACTION_DATE;
        }
        BigDecimal total = transactionRepository.sumExpensesByUserIdAndCategoryIdAndDateRange(
                userId, categoryId, startDate, endDate).setScale(MONEY_SCALE);
        return total;
    }

    private Optional<String> createBudgetWarning(Category category, Budget budget, BigDecimal totalExpense) {
        Optional<String> warning = Optional.empty();
        BigDecimal limitAmount = budget.getLimitAmount().setScale(MONEY_SCALE);
        if (totalExpense.compareTo(limitAmount) > 0) {
            BigDecimal exceededAmount = totalExpense.subtract(limitAmount);
            String message = BUDGET_WARNING_MESSAGE.formatted(category.getName(), budget.getType().getLabel(), budget.getPeriod(),
                    totalExpense.toPlainString(), limitAmount.toPlainString(), exceededAmount.toPlainString());
            warning = Optional.of(message);
            log.warn("Transaction change completed with a budget exceeded warning.");
        }
        return warning;
    }

    public TransactionDetailDTO getTransactionDetail(@NotNull @Positive Long transactionId,
            @NotNull @Positive Long userId) {
        Transaction transaction = findOwnedTransaction(transactionId, userId);
        Category category = findOwnedCategory(transaction.getCategoryId(), userId);
        TransactionDetailDTO detail = new TransactionDetailDTO(
                transaction.getId(), transaction.getAmount(), transaction.getOccurredOn(),
                category.getName(), category.getType(), transaction.getNote());
        return detail;
    }

    public TransactionDTO getTransactionForEdit(@NotNull @Positive Long transactionId,
            @NotNull @Positive Long userId) {
        Transaction transaction = findOwnedTransaction(transactionId, userId);
        TransactionDTO dto = mapToDTO(transaction);
        return dto;
    }

    @Transactional
    public Optional<String> updateTransaction(@NotNull @Positive Long transactionId, @Valid @NotNull TransactionDTO dto,
            @NotNull @Positive Long userId) {
        Transaction original = findOwnedTransactionForUpdate(transactionId, userId);
        lockAffectedCategories(userId, original.getCategoryId(), dto.getCategoryId());
        int updatedRows = transactionRepository.updateByIdAndUserId(
                transactionId, userId, dto.getCategoryId(), dto.getAmount(), dto.getOccurredOn(), dto.getNote());
        requireSuccessfulWrite(updatedRows);
        Optional<String> warning = checkBudgetAlert(userId, dto.getCategoryId(), dto.getOccurredOn());
        Optional<String> previousWarning = checkPreviousBudgetAlerts(userId, original, dto);
        warning = combineWarnings(warning, previousWarning);
        return warning;
    }

    @Transactional
    public Optional<String> deleteTransaction(@NotNull @Positive Long transactionId, @NotNull @Positive Long userId) {
        Transaction transaction = findOwnedTransactionForUpdate(transactionId, userId);
        Long categoryId = transaction.getCategoryId();
        LocalDate occurredOn = transaction.getOccurredOn();
        findOwnedCategoryForUpdate(categoryId, userId);
        int deletedRows = transactionRepository.deleteByIdAndUserId(transactionId, userId);
        requireSuccessfulWrite(deletedRows);
        Optional<String> warning = checkBudgetAlert(userId, categoryId, occurredOn);
        return warning;
    }

    private void lockAffectedCategories(Long userId, Long originalCategoryId, Long updatedCategoryId) {
        Long firstCategoryId = originalCategoryId;
        Long secondCategoryId = updatedCategoryId;
        if (originalCategoryId.compareTo(updatedCategoryId) > 0) {
            firstCategoryId = updatedCategoryId;
            secondCategoryId = originalCategoryId;
        }

        // A consistent lock order prevents opposite category moves from deadlocking.
        findOwnedCategoryForUpdate(firstCategoryId, userId);
        if (!firstCategoryId.equals(secondCategoryId)) {
            findOwnedCategoryForUpdate(secondCategoryId, userId);
        }
    }

    private Optional<String> checkPreviousBudgetAlerts(Long userId, Transaction original, TransactionDTO updated) {
        Optional<String> warning = Optional.empty();
        boolean sameCategory = original.getCategoryId().equals(updated.getCategoryId());
        for (BudgetType type : BudgetType.values()) {
            BudgetPeriod previous = BudgetPeriod.containing(type, original.getOccurredOn());
            BudgetPeriod current = BudgetPeriod.containing(type, updated.getOccurredOn());
            if (!sameCategory || !previous.equals(current)) {
                Category category = findOwnedCategory(original.getCategoryId(), userId);
                Optional<String> previousWarning = checkBudgetAlertForPeriod(userId, category, previous);
                warning = combineWarnings(warning, previousWarning);
            }
        }
        return warning;
    }

    private Optional<String> combineWarnings(Optional<String> warning, Optional<String> previousWarning) {
        if (warning.isEmpty()) {
            return previousWarning;
        }
        if (previousWarning.isEmpty()) {
            return warning;
        }

        String message = String.join(System.lineSeparator(), warning.get(), previousWarning.get());
        Optional<String> combinedWarning = Optional.of(message);
        return combinedWarning;
    }

    private Transaction findOwnedTransactionForUpdate(Long transactionId, Long userId) {
        Transaction transaction = transactionRepository.findByIdAndUserIdForUpdate(transactionId, userId)
                .orElseThrow(() -> new AccessDeniedException(TRANSACTION_ACCESS_DENIED));
        return transaction;
    }

    private Category findOwnedCategoryForUpdate(Long categoryId, Long userId) {
        Category category = categoryRepository.findByIdAndUserIdForUpdate(categoryId, userId)
                .orElseThrow(InvalidTransactionCategoryException::new);
        return category;
    }

    private Transaction findOwnedTransaction(Long transactionId, Long userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new AccessDeniedException(TRANSACTION_ACCESS_DENIED));
        return transaction;
    }

    private Category findOwnedCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(InvalidTransactionCategoryException::new);
        return category;
    }

    private void requireSuccessfulWrite(int affectedRows) {
        if (affectedRows != EXPECTED_WRITE_COUNT) {
            throw new AccessDeniedException(TRANSACTION_ACCESS_DENIED);
        }
    }

    public Page<TransactionDTO> getTransactionsByUserId(@NotNull @Positive Long userId,
            @Min(0) int page, @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        TransactionFilterDTO filters = new TransactionFilterDTO();
        Page<TransactionDTO> transactions = getTransactionsByUserId(userId, filters, pageRequest);
        return transactions;
    }

    public Page<TransactionDTO> getTransactionsByUserId(@NotNull @Positive Long userId,
            @Valid @NotNull TransactionFilterDTO filters, @NotNull Pageable pageable) {
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Direction.DESC, DATE_SORT_PROPERTY, ID_SORT_PROPERTY);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);
        Page<Transaction> transactions = transactionRepository.searchByUserId(userId, filters, sortedPageable);
        Page<TransactionDTO> transactionDTOs = transactions.map(this::mapToDTO);
        return transactionDTOs;
    }

    public DashboardDTO getDashboardData(@NotNull @Positive Long userId,
            @Min(1) @Max(12) int month, @Min(1) @Max(9999) int year) {
        YearMonth reportingMonth = YearMonth.of(year, month);
        LocalDate startDate = reportingMonth.atDay(1);
        LocalDate endDate = reportingMonth.atEndOfMonth();
        BigDecimal totalIncome = transactionRepository.sumIncomeByUserIdAndDateRange(userId, startDate, endDate);
        BigDecimal totalExpense = transactionRepository.sumExpenseByUserIdAndDateRange(userId, startDate, endDate);
        totalIncome = totalIncome.setScale(MONEY_SCALE);
        totalExpense = totalExpense.setScale(MONEY_SCALE);
        BigDecimal balance = totalIncome.subtract(totalExpense);

        DashboardDTO dashboard = new DashboardDTO(totalIncome, totalExpense, balance);
        return dashboard;
    }

    public Page<CategoryExpenseDTO> getExpensesByCategory(@NotNull @Positive Long userId,
            @NotNull YearMonth month, @NotNull Pageable pageable) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), pageSize);
        Page<CategoryExpenseDTO> expenses = transactionRepository.sumExpensesByCategoryForUserAndDateRange(
                userId, startDate, endDate, boundedPageable);
        return expenses;
    }

    public String exportTransactionsCsv(@NotNull @Positive Long userId) {
        StringBuilder csv = new StringBuilder(UTF8_BOM);
        CsvUtil.appendRow(csv, CSV_HEADER);
        Pageable pageable = PageRequest.of(0, MAX_PAGE_SIZE);
        Slice<TransactionExportDTO> transactions;
        do {
            transactions = transactionRepository.findExportRowsByUserId(userId, pageable);
            appendExportRows(csv, transactions.getContent());
            pageable = transactions.nextPageable();
        } while (transactions.hasNext());
        String exportedCsv = csv.toString();
        return exportedCsv;
    }

    private void appendExportRows(StringBuilder csv, List<TransactionExportDTO> transactions) {
        for (TransactionExportDTO transaction : transactions) {
            List<String> values = Arrays.asList(
                    transaction.occurredOn().toString(), transaction.categoryName(),
                    transaction.amount().toPlainString(), transaction.note());
            CsvUtil.appendRow(csv, values);
        }
    }

    private TransactionDTO mapToDTO(Transaction transaction) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(transaction.getId());
        dto.setAmount(transaction.getAmount());
        dto.setOccurredOn(transaction.getOccurredOn());
        dto.setNote(transaction.getNote());
        dto.setCategoryId(transaction.getCategoryId());
        return dto;
    }
}
