package com.example.expensetracker.budget;

import com.example.expensetracker.category.CategoryOptionsDTO;
import com.example.expensetracker.common.util.CategoryViewHelper;
import com.example.expensetracker.common.util.FlashMessageUtil;
import com.example.expensetracker.common.util.FormViewUtil;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

@Controller
@RequestMapping("/budgets")
public class BudgetController {

    private static final String DEFAULT_PAGE = "0";
    private static final String LIST_VIEW = "budget/list";
    private static final String FORM_VIEW = "budget/form";
    private static final String LIST_REDIRECT = "redirect:/budgets";
    private static final String SAVE_SUCCESS_MESSAGE = "Lưu ngân sách thành công!";

    private final BudgetService budgetService;
    private final CategoryViewHelper categoryViewHelper;
    private final CurrentUserService currentUserService;

    public BudgetController(BudgetService budgetService,
            CategoryViewHelper categoryViewHelper, CurrentUserService currentUserService) {
        this.budgetService = budgetService;
        this.categoryViewHelper = categoryViewHelper;
        this.currentUserService = currentUserService;
    }

    @InitBinder(BudgetDTO.MODEL_ATTRIBUTE)
    public void configureBudgetBinding(WebDataBinder binder) {
        binder.setAllowedFields("categoryId", "month", "limitAmount");
    }

    @GetMapping
    public String listBudgets(Model model, @PageableDefault(size = BudgetService.DEFAULT_PAGE_SIZE) Pageable pageable) {
        Long userId = currentUserService.getUserId();
        Page<BudgetDTO> budgets = budgetService.getBudgetsByUserId(userId, pageable);
        model.addAttribute("budgets", budgets);
        Set<Long> categoryIds = new HashSet<>();
        for (BudgetDTO budget : budgets) {
            categoryIds.add(budget.getCategoryId());
        }
        categoryViewHelper.populateCategoryDetails(model, userId, categoryIds);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showBudgetForm(Model model) {
        Long userId = currentUserService.getUserId();
        BudgetDTO dto = new BudgetDTO();
        dto.setMonth(YearMonth.now().toString());
        model.addAttribute(BudgetDTO.MODEL_ATTRIBUTE, dto);
        categoryViewHelper.populateCategoryOptions(model, userId, null);
        return FORM_VIEW;
    }

    @PostMapping
    public ModelAndView setBudget(@Valid @ModelAttribute(BudgetDTO.MODEL_ATTRIBUTE) BudgetDTO dto,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        Long userId = currentUserService.getUserId();
        if (bindingResult.hasErrors()) {
            Model model = FormViewUtil.getBindingModel(bindingResult);
            categoryViewHelper.populateCategoryOptions(model, userId, dto.getCategoryId());
            ModelAndView response = new ModelAndView(FORM_VIEW, model.asMap());
            return response;
        }

        budgetService.setBudget(dto, userId);
        FlashMessageUtil.addSuccess(redirectAttributes, SAVE_SUCCESS_MESSAGE);
        ModelAndView response = new ModelAndView(LIST_REDIRECT);
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
