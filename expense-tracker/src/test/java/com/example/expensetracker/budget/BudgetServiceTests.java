package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTests {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;
    private static final Long BUDGET_ID = 9L;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryRepository categoryRepository;
    private BudgetService budgetService;
    private BudgetDTO dto;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, categoryRepository);
        dto = new BudgetDTO();
        dto.setCategoryId(CATEGORY_ID);
        dto.setPeriod("2026-10");
        dto.setType(BudgetType.MONTHLY);
        dto.setLimitAmount(new BigDecimal("12.34"));
    }

    @Test
    void stopsBeforeBudgetPersistenceWhenCategoryIsNotOwned() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> budgetService.setBudget(dto, USER_ID))
                .isInstanceOf(InvalidBudgetCategoryException.class);
        verifyNoInteractions(budgetRepository);
    }

    @ParameterizedTest
    @EnumSource(BudgetType.class)
    void updatesOnlyOwnedExistingBudget(BudgetType type) {
        configurePeriod(type);
        stubExistingBudget();
        when(budgetRepository.updateLimitAmountByIdAndUserId(BUDGET_ID, USER_ID, dto.getLimitAmount()))
                .thenReturn(1);
        budgetService.setBudget(dto, USER_ID);
        verify(budgetRepository).updateLimitAmountByIdAndUserId(BUDGET_ID, USER_ID, dto.getLimitAmount());
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(BudgetType.class)
    void failsWhenExistingBudgetWasNotUpdated(BudgetType type) {
        configurePeriod(type);
        stubExistingBudget();
        when(budgetRepository.updateLimitAmountByIdAndUserId(BUDGET_ID, USER_ID, dto.getLimitAmount()))
                .thenReturn(0);
        assertThatThrownBy(() -> budgetService.setBudget(dto, USER_ID))
                .isInstanceOf(BudgetConflictException.class);
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(BudgetType.class)
    void createsOwnedBudgetWithCanonicalPeriodAndType(BudgetType type) {
        configurePeriod(type);
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(CATEGORY_ID);
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));

        budgetService.setBudget(dto, USER_ID);

        ArgumentCaptor<Budget> created = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).saveAndFlush(created.capture());
        assertThat(created.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(created.getValue().getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(created.getValue().getPeriod()).isEqualTo(dto.getPeriod());
        assertThat(created.getValue().getType()).isEqualTo(type);
        assertThat(created.getValue().getLimitAmount()).isEqualByComparingTo(dto.getLimitAmount());
    }

    private void configurePeriod(BudgetType type) {
        dto.setType(type);
        if (type == BudgetType.WEEKLY) {
            dto.setPeriod("2026-W43");
        } else {
            dto.setPeriod("2026-10");
        }
    }

    private void stubExistingBudget() {
        Category category = mock(Category.class);
        Budget budget = mock(Budget.class);
        when(category.getId()).thenReturn(CATEGORY_ID);
        when(budget.getId()).thenReturn(BUDGET_ID);
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriodAndType(USER_ID, CATEGORY_ID, dto.getPeriod(), dto.getType()))
                .thenReturn(Optional.of(budget));
    }
}
