package com.example.expensetracker.category;

import com.example.expensetracker.budget.BudgetRepository;
import com.example.expensetracker.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private Category category;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void mapsRequestedUserScopedListToDtosIncludingIdAndEnumType() {
        Long userId = 42L;
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(category.getId()).thenReturn(7L);
        when(category.getName()).thenReturn("Food");
        when(category.getType()).thenReturn(CategoryType.CHI);

        List<CategoryDTO> categories = categoryService.getAllCategoriesByUserId(userId);

        assertThat(categories).hasSize(1);
        CategoryDTO dto = categories.getFirst();
        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Food");
        assertThat(dto.getType()).isEqualTo("CHI");
        verify(categoryRepository).findByUserId(userId);
    }

    @Test
    void loadsOwnedCategoryForEditing() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(category.getId()).thenReturn(CATEGORY_ID);
        when(category.getName()).thenReturn("Food");
        when(category.getType()).thenReturn(CategoryType.CHI);

        CategoryDTO dto = categoryService.getCategoryForEdit(CATEGORY_ID, USER_ID);

        assertThat(dto.getId()).isEqualTo(CATEGORY_ID);
        assertThat(dto.getName()).isEqualTo("Food");
        assertThat(dto.getType()).isEqualTo("CHI");
    }

    @Test
    void deniesEditingMissingOrForeignCategory() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryForEdit(CATEGORY_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updatesOwnedCategoryNameAndTypeWithUserScopedQuery() {
        CategoryDTO dto = updateDto();
        dto.setId(999L);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.updateByIdAndUserId(CATEGORY_ID, USER_ID, "Salary", CategoryType.THU))
                .thenReturn(1);

        categoryService.updateCategory(CATEGORY_ID, dto, USER_ID);

        verify(categoryRepository).updateByIdAndUserId(CATEGORY_ID, USER_ID, "Salary", CategoryType.THU);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deniesUpdatingMissingOrForeignCategoryBeforeWriting() {
        CategoryDTO dto = updateDto();
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, dto, USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(categoryRepository, never()).updateByIdAndUserId(anyLong(), anyLong(), any(), any());
    }

    @Test
    void doesNotReportSuccessWhenUpdateAffectsNoRows() {
        CategoryDTO dto = updateDto();
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, dto, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletesUnusedOwnedCategory() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.deleteByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(1);

        categoryService.deleteCategory(CATEGORY_ID, USER_ID);

        verify(transactionRepository).countByCategoryIdAndUserId(CATEGORY_ID, USER_ID);
        verify(budgetRepository).existsByCategoryIdAndUserId(CATEGORY_ID, USER_ID);
        verify(categoryRepository).deleteByIdAndUserId(CATEGORY_ID, USER_ID);
    }

    @Test
    void deniesDeletingMissingOrForeignCategoryBeforeCheckingUsage() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(transactionRepository, budgetRepository);
        verify(categoryRepository, never()).deleteByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void preventsDeletingCategoryWithTransactions() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.countByCategoryIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID, USER_ID))
                .isInstanceOf(CategoryInUseException.class);

        verifyNoInteractions(budgetRepository);
        verify(categoryRepository, never()).deleteByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void preventsDeletingCategoryWithBudgetsEvenWithoutTransactions() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByCategoryIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID, USER_ID))
                .isInstanceOf(CategoryInUseException.class);

        verify(categoryRepository, never()).deleteByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void preservesUsageQueryFailureInsteadOfDeletingCategory() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        IllegalStateException failure = new IllegalStateException("Usage query failed");
        when(transactionRepository.countByCategoryIdAndUserId(CATEGORY_ID, USER_ID)).thenThrow(failure);

        assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID, USER_ID)).isSameAs(failure);

        verifyNoInteractions(budgetRepository);
        verify(categoryRepository, never()).deleteByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void doesNotReportSuccessWhenDeleteAffectsNoRows() {
        when(categoryRepository.findByIdAndUserIdForUpdate(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    private CategoryDTO updateDto() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("  Salary  ");
        dto.setType("THU");
        return dto;
    }
}
