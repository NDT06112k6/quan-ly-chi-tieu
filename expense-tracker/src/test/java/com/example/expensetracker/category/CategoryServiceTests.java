package com.example.expensetracker.category;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private Category category;

    @Test
    void mapsRequestedUserScopedListToDtosIncludingIdAndEnumType() {
        Long userId = 42L;
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category));
        when(category.getId()).thenReturn(7L);
        when(category.getName()).thenReturn("Food");
        when(category.getType()).thenReturn(CategoryType.CHI);
        CategoryService categoryService = new CategoryService(categoryRepository);

        List<CategoryDTO> categories = categoryService.getAllCategoriesByUserId(userId);

        assertThat(categories).hasSize(1);
        CategoryDTO dto = categories.getFirst();
        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Food");
        assertThat(dto.getType()).isEqualTo("CHI");
        verify(categoryRepository).findByUserId(userId);
    }
}
