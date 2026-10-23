package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.home.DashboardDTO;
import com.example.expensetracker.home.CategoryExpenseDTO;
import com.example.expensetracker.common.util.CsvUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
public class TransactionService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String DATE_SORT_PROPERTY = "occurredOn";
    private static final String ID_SORT_PROPERTY = "id";
    private static final int MONEY_SCALE = 2;
    private static final String UTF8_BOM = "\uFEFF";
    private static final List<String> CSV_HEADER = List.of("Ngày", "Danh mục", "Số tiền", "Ghi chú");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void createTransaction(@Valid @NotNull TransactionDTO dto, @NotNull @Positive Long userId) {
        Category category = categoryRepository.findByIdAndUserId(dto.getCategoryId(), userId)
                .orElseThrow(InvalidTransactionCategoryException::new);

        Transaction transaction = new Transaction(
                userId, category.getId(), dto.getAmount(), dto.getOccurredOn(), dto.getNote());
        transactionRepository.saveAndFlush(transaction);
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
        BigDecimal totalIncome = transactionRepository.sumIncomeByUserIdAndMonth(userId, month, year);
        BigDecimal totalExpense = transactionRepository.sumExpenseByUserIdAndMonth(userId, month, year);
        totalIncome = totalIncome.setScale(MONEY_SCALE);
        totalExpense = totalExpense.setScale(MONEY_SCALE);
        BigDecimal balance = totalIncome.subtract(totalExpense);

        DashboardDTO dashboard = new DashboardDTO(totalIncome, totalExpense, balance);
        return dashboard;
    }

    public Page<CategoryExpenseDTO> getExpensesByCategory(@NotNull @Positive Long userId,
            @NotNull YearMonth month, @NotNull Pageable pageable) {
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), pageSize);
        Page<CategoryExpenseDTO> expenses = transactionRepository.sumExpensesByCategoryForUserAndMonth(
                userId, month, boundedPageable);
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
