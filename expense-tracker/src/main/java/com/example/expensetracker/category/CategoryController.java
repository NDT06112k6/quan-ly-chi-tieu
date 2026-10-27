package com.example.expensetracker.category;

import com.example.expensetracker.common.util.FlashMessageUtil;
import com.example.expensetracker.common.util.FormViewUtil;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private static final String LIST_VIEW = "category/list";
    private static final String FORM_VIEW = "category/form";
    private static final String LIST_REDIRECT = "redirect:/categories";
    private static final String LIST_MODEL_ATTRIBUTE = "categories";
    private static final String CREATE_SUCCESS_MESSAGE = "Thêm danh mục thành công!";
    private static final String UPDATE_SUCCESS_MESSAGE = "Cập nhật danh mục thành công!";
    private static final String DELETE_SUCCESS_MESSAGE = "Xóa danh mục thành công!";

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

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") @Positive Long id, Model model) {
        Long userId = currentUserService.getUserId();
        CategoryDTO dto = categoryService.getCategoryForEdit(id, userId);
        model.addAttribute(CategoryDTO.MODEL_ATTRIBUTE, dto);
        return FORM_VIEW;
    }

    @PostMapping("/{id}/edit")
    public ModelAndView updateCategory(@PathVariable("id") @Positive Long id,
            @Valid @ModelAttribute(CategoryDTO.MODEL_ATTRIBUTE) CategoryDTO dto, BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        Long userId = currentUserService.getUserId();
        if (bindingResult.hasErrors()) {
            ModelAndView response = buildInvalidEditResponse(id, userId, bindingResult);
            return response;
        }

        categoryService.updateCategory(id, dto, userId);
        FlashMessageUtil.addSuccess(redirectAttributes, UPDATE_SUCCESS_MESSAGE);
        ModelAndView response = new ModelAndView(LIST_REDIRECT);
        return response;
    }

    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable("id") @Positive Long id, RedirectAttributes redirectAttributes) {
        Long userId = currentUserService.getUserId();
        categoryService.deleteCategory(id, userId);
        FlashMessageUtil.addSuccess(redirectAttributes, DELETE_SUCCESS_MESSAGE);
        return LIST_REDIRECT;
    }

    private ModelAndView buildInvalidEditResponse(Long id, Long userId, BindingResult bindingResult) {
        categoryService.getCategoryForEdit(id, userId);
        Model model = FormViewUtil.getBindingModel(bindingResult);
        CategoryDTO dto = (CategoryDTO) model.getAttribute(CategoryDTO.MODEL_ATTRIBUTE);
        dto.setId(id);
        ModelAndView response = new ModelAndView(FORM_VIEW, model.asMap());
        response.setStatus(HttpStatus.BAD_REQUEST);
        return response;
    }

    private Long getCurrentUserId(Authentication authentication) {
        Long userId = currentUserService.getUserId(authentication);
        return userId;
    }
}
