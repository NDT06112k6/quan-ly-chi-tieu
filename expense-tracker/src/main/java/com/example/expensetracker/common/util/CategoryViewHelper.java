package com.example.expensetracker.common.util;

import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.category.CategoryOptionsDTO;
import com.example.expensetracker.category.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class CategoryViewHelper {

    private final CategoryService categoryService;

    public CategoryViewHelper(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public CategoryOptionsDTO getCategoryOptions(Long userId, int page) {
        Page<CategoryDTO> categories = getCategoryPage(userId, page);
        CategoryOptionsDTO options = new CategoryOptionsDTO(
                categories.getContent(), categories.getNumber(), categories.hasNext());
        return options;
    }

    public void populateCategoryOptions(Model model, Long userId, Long selectedCategoryId) {
        Page<CategoryDTO> categoryPage = getCategoryPage(userId, 0);
        List<CategoryDTO> categories = new ArrayList<>(categoryPage.getContent());
        includeSelectedCategory(categories, selectedCategoryId, userId);
        model.addAttribute("categories", categories);
        model.addAttribute("hasMoreCategories", categoryPage.hasNext());
    }

    public void populateCategoryDetails(Model model, Long userId, Set<Long> categoryIds) {
        List<CategoryDTO> categories = categoryService.getCategoriesByIdsForUser(userId, categoryIds);
        Map<Long, String> categoryNames = new HashMap<>();
        Map<Long, String> categoryTypes = new HashMap<>();
        for (CategoryDTO category : categories) {
            categoryNames.put(category.getId(), category.getName());
            categoryTypes.put(category.getId(), category.getType());
        }
        model.addAttribute("categoryNames", categoryNames);
        model.addAttribute("categoryTypes", categoryTypes);
    }

    private Page<CategoryDTO> getCategoryPage(Long userId, int page) {
        PageRequest pageRequest = PageRequest.of(page, CategoryService.MAX_PAGE_SIZE);
        Page<CategoryDTO> categories = categoryService.getAllCategoriesByUserId(userId, pageRequest);
        return categories;
    }

    private void includeSelectedCategory(List<CategoryDTO> categories, Long selectedCategoryId, Long userId) {
        if (selectedCategoryId == null) {
            return;
        }
        for (CategoryDTO category : categories) {
            if (category.getId().equals(selectedCategoryId)) {
                return;
            }
        }

        Optional<CategoryDTO> selectedCategory = categoryService.getCategoryByIdAndUserId(selectedCategoryId, userId);
        if (selectedCategory.isPresent()) {
            categories.add(selectedCategory.get());
        }
    }
}
