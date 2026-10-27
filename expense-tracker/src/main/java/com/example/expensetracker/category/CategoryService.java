package com.example.expensetracker.category;

import com.example.expensetracker.budget.BudgetRepository;
import com.example.expensetracker.transaction.TransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
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
    private static final String ACCESS_DENIED_MESSAGE = "Không thể truy cập danh mục này.";
    private static final int EXPECTED_WRITE_COUNT = 1;

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public CategoryService(CategoryRepository categoryRepository, TransactionRepository transactionRepository,
            BudgetRepository budgetRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    @Transactional
    public void createCategory(@Valid @NotNull CategoryDTO dto, @NotNull @Positive Long userId) {
        String name = dto.getName().strip();
        CategoryType type = CategoryType.valueOf(dto.getType());
        Category category = new Category(userId, name, type);
        categoryRepository.save(category);
    }

    public CategoryDTO getCategoryForEdit(@NotNull @Positive Long id, @NotNull @Positive Long userId) {
        Category category = findOwnedCategory(id, userId);
        CategoryDTO dto = mapToDTO(category);
        return dto;
    }

    @Transactional
    public void updateCategory(@NotNull @Positive Long id, @Valid @NotNull CategoryDTO dto,
            @NotNull @Positive Long userId) {
        findOwnedCategory(id, userId);
        String name = dto.getName().strip();
        CategoryType type = CategoryType.valueOf(dto.getType());
        int updatedRows = categoryRepository.updateByIdAndUserId(id, userId, name, type);
        requireSuccessfulWrite(updatedRows);
    }

    @Transactional
    public void deleteCategory(@NotNull @Positive Long id, @NotNull @Positive Long userId) {
        // The lock prevents new foreign-key references between the usage checks and deletion.
        categoryRepository.findByIdAndUserIdForUpdate(id, userId)
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
        long transactionCount = transactionRepository.countByCategoryIdAndUserId(id, userId);
        if (transactionCount > 0) {
            throw new CategoryInUseException();
        }
        if (budgetRepository.existsByCategoryIdAndUserId(id, userId)) {
            throw new CategoryInUseException();
        }

        int deletedRows = categoryRepository.deleteByIdAndUserId(id, userId);
        requireSuccessfulWrite(deletedRows);
    }

    private Category findOwnedCategory(Long id, Long userId) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
        return category;
    }

    private void requireSuccessfulWrite(int affectedRows) {
        if (affectedRows != EXPECTED_WRITE_COUNT) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }
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
