package com.example.expensetracker.category;

import com.example.expensetracker.common.util.FlashMessageUtil;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private static final String LIST_VIEW = "category/list";
    private static final String FORM_VIEW = "category/form";
    private static final String LIST_REDIRECT = "redirect:/categories";
    private static final String LIST_MODEL_ATTRIBUTE = "categories";
    private static final String CREATE_SUCCESS_MESSAGE = "Thêm danh mục thành công!";

    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    public CategoryController(CategoryService categoryService, CurrentUserService currentUserService) {
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
    }

    @InitBinder(CategoryDTO.MODEL_ATTRIBUTE)
    public void configureCategoryBinding(WebDataBinder binder) {
        binder.setAllowedFields("name", "type");
    }

    @ModelAttribute("categoryTypes")
    public CategoryType[] getCategoryTypes() {
        CategoryType[] categoryTypes = CategoryType.values();
        return categoryTypes;
    }

    @GetMapping
    public String listCategories(Model model, Authentication authentication,
            @PageableDefault(size = CategoryService.DEFAULT_PAGE_SIZE) Pageable pageable) {
        Long userId = getCurrentUserId(authentication);
        Page<CategoryDTO> categories = categoryService.getAllCategoriesByUserId(userId, pageable);
        model.addAttribute(LIST_MODEL_ATTRIBUTE, categories);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showCategoryForm(Model model) {
        CategoryDTO dto = new CategoryDTO();
        model.addAttribute(CategoryDTO.MODEL_ATTRIBUTE, dto);
        return FORM_VIEW;
    }

    @PostMapping
    public String createCategory(@Valid @ModelAttribute(CategoryDTO.MODEL_ATTRIBUTE) CategoryDTO dto,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        Long userId = currentUserService.getUserId();
        categoryService.createCategory(dto, userId);
        FlashMessageUtil.addSuccess(redirectAttributes, CREATE_SUCCESS_MESSAGE);
        return LIST_REDIRECT;
    }

    private Long getCurrentUserId(Authentication authentication) {
        Long userId = currentUserService.getUserId(authentication);
        return userId;
    }
}
