package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.category.CategoryOptionsDTO;
import com.example.expensetracker.common.util.CategoryViewHelper;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    public static final String EXPORT_PATH = "/transactions/export";
    private static final String DEFAULT_PAGE = "0";
    private static final String LIST_VIEW = "transaction/list";
    private static final String FORM_VIEW = "transaction/form";
    private static final String DETAIL_VIEW = "transaction/detail";
    private static final String DETAIL_REDIRECT = "redirect:/transactions/";
    private static final String LIST_REDIRECT = "redirect:/transactions";
    private static final String CSV_ATTACHMENT = "attachment; filename=transactions.csv";
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);

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

    @InitBinder(TransactionFilterDTO.MODEL_ATTRIBUTE)
    public void configureFilterBinding(WebDataBinder binder) {
        binder.setAllowedFields("keyword", "categoryId", "startDate", "endDate");
    }

    @GetMapping
    public String listTransactions(Model model, @Min(0) @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @Valid @ModelAttribute(TransactionFilterDTO.MODEL_ATTRIBUTE) TransactionFilterDTO filters) {
        Long userId = currentUserService.getUserId();
        Pageable pageable = PageRequest.of(page, TransactionService.DEFAULT_PAGE_SIZE);
        Page<TransactionDTO> transactions = transactionService.getTransactionsByUserId(userId, filters, pageable);
        model.addAttribute("transactions", transactions);
        Set<Long> categoryIds = new HashSet<>();
        for (TransactionDTO transaction : transactions) {
            categoryIds.add(transaction.getCategoryId());
        }
        categoryViewHelper.populateCategoryDetails(model, userId, categoryIds);
        categoryViewHelper.populateCategoryOptions(model, userId, filters.getCategoryId());
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showTransactionForm(Model model) {
        Long userId = currentUserService.getUserId();
        CategoryOptionsDTO categoryOptions = categoryViewHelper.getCategoryOptions(userId, 0);
        List<CategoryDTO> categories = categoryOptions.content();
        TransactionDTO dto = new TransactionDTO();
        model.addAttribute(TransactionDTO.MODEL_ATTRIBUTE, dto);
        model.addAttribute("categories", categories);
        model.addAttribute("hasMoreCategories", categoryOptions.hasNext());
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

    @GetMapping("/{id}")
    public String showTransactionDetail(@PathVariable("id") @Positive Long transactionId,
            Authentication authentication, Model model) {
        Long userId = currentUserService.getUserId(authentication);
        TransactionDetailDTO transaction = transactionService.getTransactionDetail(transactionId, userId);
        model.addAttribute("transaction", transaction);
        return DETAIL_VIEW;
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") @Positive Long transactionId,
            Authentication authentication, Model model) {
        Long userId = currentUserService.getUserId(authentication);
        TransactionDTO dto = transactionService.getTransactionForEdit(transactionId, userId);
        model.addAttribute(TransactionDTO.MODEL_ATTRIBUTE, dto);
        populateEditModel(model, transactionId, userId);
        return FORM_VIEW;
    }

    @PostMapping("/{id}/edit")
    public ModelAndView updateTransaction(@PathVariable("id") @Positive Long transactionId,
            @Valid @ModelAttribute(TransactionDTO.MODEL_ATTRIBUTE) TransactionDTO dto, BindingResult bindingResult) {
        Long userId = currentUserService.getUserId();
        if (bindingResult.hasErrors()) {
            transactionService.getTransactionForEdit(transactionId, userId);
            Model model = new ExtendedModelMap();
            model.addAllAttributes(bindingResult.getModel());
            populateEditModel(model, transactionId, userId);
            ModelAndView response = new ModelAndView(FORM_VIEW, model.asMap());
            response.setStatus(HttpStatus.BAD_REQUEST);
            return response;
        }

        transactionService.updateTransaction(transactionId, dto, userId);
        ModelAndView response = new ModelAndView(DETAIL_REDIRECT + transactionId);
        return response;
    }

    @PostMapping("/{id}/delete")
    public String deleteTransaction(@PathVariable("id") @Positive Long transactionId,
            Authentication authentication) {
        Long userId = currentUserService.getUserId(authentication);
        transactionService.deleteTransaction(transactionId, userId);
        return LIST_REDIRECT;
    }

    private void populateEditModel(Model model, Long transactionId, Long userId) {
        TransactionDTO dto = (TransactionDTO) model.getAttribute(TransactionDTO.MODEL_ATTRIBUTE);
        model.addAttribute("editing", true);
        model.addAttribute("transactionId", transactionId);
        categoryViewHelper.populateCategoryOptions(model, userId, dto.getCategoryId());
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportTransactions() {
        Long userId = currentUserService.getUserId();
        String csv = transactionService.exportTransactionsCsv(userId);
        ResponseEntity<String> response = ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, CSV_ATTACHMENT)
                .cacheControl(CacheControl.noStore())
                .body(csv);
        return response;
    }

    @GetMapping("/categories")
    @ResponseBody
    public CategoryOptionsDTO getCategoryOptions(@Min(0) @RequestParam(defaultValue = DEFAULT_PAGE) int page) {
        Long userId = currentUserService.getUserId();
        CategoryOptionsDTO options = categoryViewHelper.getCategoryOptions(userId, page);
        return options;
    }
}
