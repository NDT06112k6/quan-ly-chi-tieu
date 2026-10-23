package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.category.CategoryService;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private static final String DEFAULT_PAGE = "0";
    private static final String LIST_VIEW = "transaction/list";
    private static final String FORM_VIEW = "transaction/form";
    private static final String LIST_REDIRECT = "redirect:/transactions";

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    public TransactionController(TransactionService transactionService,
            CategoryService categoryService, CurrentUserService currentUserService) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
    }

    @InitBinder(TransactionDTO.MODEL_ATTRIBUTE)
    public void configureTransactionBinding(WebDataBinder binder) {
        binder.setAllowedFields("amount", "occurredOn", "note", "categoryId");
    }

    @GetMapping
    public String listTransactions(Model model, @RequestParam(defaultValue = DEFAULT_PAGE) int page) {
        Long userId = getCurrentUserId();
        Page<TransactionDTO> transactions = transactionService.getTransactionsByUserId(
                userId, page, TransactionService.DEFAULT_PAGE_SIZE);
        model.addAttribute("transactions", transactions);
        populateCategoryNames(model, userId, transactions.getContent());
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showTransactionForm(Model model) {
        Long userId = getCurrentUserId();
        TransactionDTO dto = new TransactionDTO();
        model.addAttribute(TransactionDTO.MODEL_ATTRIBUTE, dto);
        populateCategoryOptions(model, userId, null);
        return FORM_VIEW;
    }

    @PostMapping
    public String createTransaction(@Valid @ModelAttribute(TransactionDTO.MODEL_ATTRIBUTE) TransactionDTO dto,
            BindingResult bindingResult, Model model) {
        Long userId = getCurrentUserId();
        if (bindingResult.hasErrors()) {
            populateCategoryOptions(model, userId, dto.getCategoryId());
            return FORM_VIEW;
        }

        transactionService.createTransaction(dto, userId);
        return LIST_REDIRECT;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = currentUserService.getUserId(authentication);
        return userId;
    }

    @GetMapping("/categories")
    @ResponseBody
    public CategoryOptionsDTO getCategoryOptions(@Min(0) @RequestParam(defaultValue = DEFAULT_PAGE) int page) {
        Long userId = getCurrentUserId();
        PageRequest pageRequest = PageRequest.of(page, CategoryService.MAX_PAGE_SIZE);
        Page<CategoryDTO> categories = categoryService.getAllCategoriesByUserId(userId, pageRequest);
        CategoryOptionsDTO options = new CategoryOptionsDTO(
                categories.getContent(), categories.getNumber(), categories.hasNext());
        return options;
    }

    private void populateCategoryOptions(Model model, Long userId, Long selectedCategoryId) {
        PageRequest pageRequest = PageRequest.of(0, CategoryService.MAX_PAGE_SIZE);
        Page<CategoryDTO> categoryPage = categoryService.getAllCategoriesByUserId(userId, pageRequest);
        List<CategoryDTO> categories = new ArrayList<>(categoryPage.getContent());
        includeSelectedCategory(categories, selectedCategoryId, userId);
        model.addAttribute("categories", categories);
        model.addAttribute("hasMoreCategories", categoryPage.hasNext());
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

    private void populateCategoryNames(Model model, Long userId, List<TransactionDTO> transactions) {
        Set<Long> categoryIds = new HashSet<>();
        for (TransactionDTO transaction : transactions) {
            categoryIds.add(transaction.getCategoryId());
        }
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
}
