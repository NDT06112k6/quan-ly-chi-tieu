package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.home.DashboardDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@Validated
@Transactional(readOnly = true)
public class TransactionService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String DATE_SORT_PROPERTY = "occurredOn";
    private static final String ID_SORT_PROPERTY = "id";
    private static final int MONEY_SCALE = 2;

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
        Sort sort = Sort.by(Sort.Direction.DESC, DATE_SORT_PROPERTY, ID_SORT_PROPERTY);
        PageRequest sortedPageRequest = pageRequest.withSort(sort);
        Page<Transaction> transactions = transactionRepository.findByUserId(userId, sortedPageRequest);
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
