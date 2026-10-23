package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.common.util.MonthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.Optional;

@Service
@Validated
@Transactional(readOnly = true)
public class BudgetService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String MONTH_SORT_PROPERTY = "month";
    private static final String ID_SORT_PROPERTY = "id";
    private static final int EXPECTED_UPDATE_COUNT = 1;

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void setBudget(@Valid @NotNull BudgetDTO dto, @NotNull @Positive Long userId) {
        // Serialize budget writes for the same owned category, including creation of a new month.
        Category category = categoryRepository.findByIdAndUserIdForUpdate(dto.getCategoryId(), userId)
                .orElseThrow(InvalidBudgetCategoryException::new);
        Optional<Budget> existingBudget = budgetRepository.findByUserIdAndCategoryIdAndMonth(
                userId, category.getId(), dto.getMonth());
        if (existingBudget.isPresent()) {
            updateExistingBudget(existingBudget.get(), dto, userId);
            return;
        }

        LocalDate month = MonthUtil.firstDayOfMonth(dto.getMonth());
        Budget budget = new Budget(userId, category.getId(), month, dto.getLimitAmount());
        budgetRepository.saveAndFlush(budget);
    }

    public Page<BudgetDTO> getBudgetsByUserId(@NotNull @Positive Long userId) {
        Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE);
        Page<BudgetDTO> budgets = getBudgetsByUserId(userId, pageable);
        return budgets;
    }

    public Page<BudgetDTO> getBudgetsByUserId(@NotNull @Positive Long userId, Pageable pageable) {
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Direction.DESC, MONTH_SORT_PROPERTY, ID_SORT_PROPERTY);
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);
        Page<Budget> budgets = budgetRepository.findByUserId(userId, boundedPageable);
        Page<BudgetDTO> budgetDTOs = budgets.map(this::mapToDTO);
        return budgetDTOs;
    }

    private void updateExistingBudget(Budget budget, BudgetDTO dto, Long userId) {
        int updatedRows = budgetRepository.updateLimitAmountByIdAndUserId(
                budget.getId(), userId, dto.getLimitAmount());
        if (updatedRows != EXPECTED_UPDATE_COUNT) {
            throw new BudgetConflictException();
        }
    }

    private BudgetDTO mapToDTO(Budget budget) {
        BudgetDTO dto = new BudgetDTO();
        dto.setId(budget.getId());
        dto.setCategoryId(budget.getCategoryId());
        dto.setMonth(MonthUtil.formatMonth(budget.getMonth()));
        dto.setLimitAmount(budget.getLimitAmount());
        return dto;
    }
}
