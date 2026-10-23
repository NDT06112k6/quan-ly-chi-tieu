package com.example.expensetracker.category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Validated
@Transactional(readOnly = true)
public class CategoryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String SORT_PROPERTY = "id";

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void createCategory(@Valid @NotNull CategoryDTO dto, @NotNull @Positive Long userId) {
        String name = dto.getName().strip();
        CategoryType type = CategoryType.valueOf(dto.getType());
        Category category = new Category(userId, name, type);
        categoryRepository.save(category);
    }

    public List<CategoryDTO> getAllCategoriesByUserId(@NotNull @Positive Long userId) {
        List<Category> categories = categoryRepository.findByUserId(userId);
        List<CategoryDTO> categoryDTOs = new ArrayList<>();
        for (Category category : categories) {
            CategoryDTO dto = mapToDTO(category);
            categoryDTOs.add(dto);
        }
        return categoryDTOs;
    }

    public Page<CategoryDTO> getAllCategoriesByUserId(@NotNull @Positive Long userId, Pageable pageable) {
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = Sort.by(SORT_PROPERTY).ascending();
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);
        Page<Category> categories = categoryRepository.findByUserId(userId, boundedPageable);
        Page<CategoryDTO> categoryDTOs = categories.map(this::mapToDTO);
        return categoryDTOs;
    }

    private CategoryDTO mapToDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setType(category.getType().name());
        return dto;
    }

    public Optional<CategoryDTO> getCategoryByIdAndUserId(Long categoryId, Long userId) {
        Optional<CategoryDTO> category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .map(this::mapToDTO);
        return category;
    }

    public List<CategoryDTO> getCategoriesByIdsForUser(@NotNull @Positive Long userId,
            @NotNull @Size(max = MAX_PAGE_SIZE) Set<Long> categoryIds) {
        List<CategoryDTO> categories = new ArrayList<>();
        if (categoryIds.isEmpty()) {
            return categories;
        }

        Pageable pageable = PageRequest.of(0, MAX_PAGE_SIZE);
        Page<Category> categoryPage = categoryRepository.findByUserIdAndIdIn(userId, categoryIds, pageable);
        for (Category category : categoryPage) {
            CategoryDTO dto = mapToDTO(category);
            categories.add(dto);
        }
        return categories;
    }
}
