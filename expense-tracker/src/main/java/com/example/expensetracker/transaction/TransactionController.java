package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryOptionsDTO;
import com.example.expensetracker.common.util.CategoryViewHelper;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
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

import java.util.HashSet;
import java.util.Set;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private static final String DEFAULT_PAGE = "0";
    private static final String LIST_VIEW = "transaction/list";
    private static final String FORM_VIEW = "transaction/form";
    private static final String LIST_REDIRECT = "redirect:/transactions";

    private final TransactionService transactionService;
    private final CategoryViewHelper categoryViewHelper;
    private final CurrentUserService currentUserService;

    public TransactionController(TransactionService transactionService,
            CategoryViewHelper categoryViewHelper, CurrentUserService currentUserService) {
        this.transactionService = transactionService;
        this.categoryViewHelper = categoryViewHelper;
        this.currentUserService = currentUserService;
    }

    @InitBinder(TransactionDTO.MODEL_ATTRIBUTE)
    public void configureTransactionBinding(WebDataBinder binder) {
        binder.setAllowedFields("amount", "occurredOn", "note", "categoryId");
    }

    @GetMapping
    public String listTransactions(Model model, @RequestParam(defaultValue = DEFAULT_PAGE) int page) {
        Long userId = currentUserService.getUserId();
        Page<TransactionDTO> transactions = transactionService.getTransactionsByUserId(
                userId, page, TransactionService.DEFAULT_PAGE_SIZE);
        model.addAttribute("transactions", transactions);
        Set<Long> categoryIds = new HashSet<>();
        for (TransactionDTO transaction : transactions) {
            categoryIds.add(transaction.getCategoryId());
        }
        categoryViewHelper.populateCategoryDetails(model, userId, categoryIds);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showTransactionForm(Model model) {
        Long userId = currentUserService.getUserId();
        TransactionDTO dto = new TransactionDTO();
        model.addAttribute(TransactionDTO.MODEL_ATTRIBUTE, dto);
        categoryViewHelper.populateCategoryOptions(model, userId, null);
        return FORM_VIEW;
    }

    @PostMapping
    public String createTransaction(@Valid @ModelAttribute(TransactionDTO.MODEL_ATTRIBUTE) TransactionDTO dto,
            BindingResult bindingResult, Model model) {
        Long userId = currentUserService.getUserId();
        if (bindingResult.hasErrors()) {
            categoryViewHelper.populateCategoryOptions(model, userId, dto.getCategoryId());
            return FORM_VIEW;
        }

        transactionService.createTransaction(dto, userId);
        return LIST_REDIRECT;
    }

    @GetMapping("/categories")
    @ResponseBody
    public CategoryOptionsDTO getCategoryOptions(@Min(0) @RequestParam(defaultValue = DEFAULT_PAGE) int page) {
        Long userId = currentUserService.getUserId();
        CategoryOptionsDTO options = categoryViewHelper.getCategoryOptions(userId, page);
        return options;
    }
}
