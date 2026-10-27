package com.example.expensetracker.category;

import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = UserDetailsService.class)
class CategoryControllerTests {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 7L;
    private static final String USERNAME = "category-view@example.invalid";
    private static final String EDIT_PATH = "/categories/7/edit";
    private static final String DELETE_PATH = "/categories/7/delete";

    private final MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private UserService userService;

    @Autowired
    CategoryControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void configureAuthenticatedUser() {
        when(currentUserService.getUserId()).thenReturn(USER_ID);
        when(currentUserService.getUserId(any(Authentication.class))).thenReturn(USER_ID);
        when(userService.getProfile(USERNAME)).thenReturn(new UserProfileDTO(USERNAME, "Minh"));
    }

    @Test
    void rendersEditFormWithOwnedValuesAndCsrfToken() throws Exception {
        when(categoryService.getCategoryForEdit(CATEGORY_ID, USER_ID)).thenReturn(categoryDto());

        mockMvc.perform(get(EDIT_PATH).with(user(USERNAME)).param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(view().name("category/form"))
                .andExpect(content().string(containsString("Sửa danh mục")))
                .andExpect(content().string(containsString("action=\"" + EDIT_PATH + "\"")))
                .andExpect(content().string(containsString("value=\"Food\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verify(categoryService).getCategoryForEdit(CATEGORY_ID, USER_ID);
    }

    @Test
    void keepsCreationFormPostingToCreationEndpoint() throws Exception {
        mockMvc.perform(get("/categories/new").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thêm danh mục")))
                .andExpect(content().string(containsString("action=\"/categories\"")));
    }

    @Test
    void rendersEditLinksAndPostDeleteFormsInList() throws Exception {
        when(categoryService.getAllCategoriesByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(categoryDto())));

        mockMvc.perform(get("/categories").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"" + EDIT_PATH + "\"")))
                .andExpect(content().string(containsString("method=\"post\" action=\"" + DELETE_PATH + "\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void updatesUsingPathIdAndAuthenticatedOwnerAndIgnoresSubmittedIds() throws Exception {
        mockMvc.perform(editRequest("Salary", "THU").param("id", "99").param("userId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("success", "Cập nhật danh mục thành công!"));

        ArgumentCaptor<CategoryDTO> dto = ArgumentCaptor.forClass(CategoryDTO.class);
        verify(categoryService).updateCategory(eq(CATEGORY_ID), dto.capture(), eq(USER_ID));
        assertThat(dto.getValue().getId()).isNull();
        assertThat(dto.getValue().getName()).isEqualTo("Salary");
        assertThat(dto.getValue().getType()).isEqualTo("THU");
    }

    @ParameterizedTest
    @CsvSource(value = {"'',CHI,name", "Food,OTHER,type", "Food,'',type"})
    void retainsEditActionAndFieldErrorsAfterInvalidInput(String name, String type, String invalidField)
            throws Exception {
        when(categoryService.getCategoryForEdit(CATEGORY_ID, USER_ID)).thenReturn(categoryDto());

        mockMvc.perform(editRequest(name, type).param("id", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("category/form"))
                .andExpect(model().attributeHasFieldErrors(CategoryDTO.MODEL_ATTRIBUTE, invalidField))
                .andExpect(content().string(containsString("Sửa danh mục")))
                .andExpect(content().string(containsString("action=\"" + EDIT_PATH + "\"")))
                .andExpect(flash().attributeCount(0));

        verify(categoryService, never()).updateCategory(any(), any(), any());
    }

    @Test
    void deniesForeignCategoryEvenWhenSubmittedFormIsInvalid() throws Exception {
        when(categoryService.getCategoryForEdit(CATEGORY_ID, USER_ID)).thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(editRequest("", "CHI"))
                .andExpect(status().isForbidden())
                .andExpect(flash().attributeCount(0));

        verify(categoryService, never()).updateCategory(any(), any(), any());
    }

    @Test
    void deniesReadingForeignCategoryEditForm() throws Exception {
        when(categoryService.getCategoryForEdit(CATEGORY_ID, USER_ID)).thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(get(EDIT_PATH).with(user(USERNAME)))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotFlashSuccessAfterDeniedUpdate() throws Exception {
        doThrow(new AccessDeniedException("Denied"))
                .when(categoryService).updateCategory(eq(CATEGORY_ID), any(CategoryDTO.class), eq(USER_ID));

        mockMvc.perform(editRequest("Salary", "THU"))
                .andExpect(status().isForbidden())
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void deletesUsingAuthenticatedOwnerAndFlashesSuccess() throws Exception {
        mockMvc.perform(post(DELETE_PATH).with(user(USERNAME)).with(csrf()).param("userId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("success", "Xóa danh mục thành công!"));

        verify(categoryService).deleteCategory(CATEGORY_ID, USER_ID);
    }

    @Test
    void redirectsInUseCategoryToListWithErrorToastAndNoSuccess() throws Exception {
        CategoryInUseException failure = new CategoryInUseException();
        doThrow(failure).when(categoryService).deleteCategory(CATEGORY_ID, USER_ID);

        MvcResult result = mockMvc.perform(post(DELETE_PATH).with(user(USERNAME)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("error", failure.getMessage()))
                .andExpect(flash().attributeCount(1))
                .andReturn();
        when(categoryService.getAllCategoriesByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(categoryDto())));

        mockMvc.perform(get("/categories").with(user(USERNAME)).flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"toast-error\"")))
                .andExpect(content().string(containsString(failure.getMessage())));
    }

    @Test
    void deniesDeletingForeignCategoryWithoutSuccessMessage() throws Exception {
        doThrow(new AccessDeniedException("Denied"))
                .when(categoryService).deleteCategory(CATEGORY_ID, USER_ID);

        mockMvc.perform(post(DELETE_PATH).with(user(USERNAME)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void preservesUnexpectedDeleteFailure() {
        doThrow(new IllegalStateException("Database unavailable"))
                .when(categoryService).deleteCategory(CATEGORY_ID, USER_ID);

        assertThatThrownBy(() -> mockMvc.perform(post(DELETE_PATH).with(user(USERNAME)).with(csrf())))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {EDIT_PATH, DELETE_PATH})
    void requiresAuthenticationForMutations(String path) throws Exception {
        mockMvc.perform(post(path).with(csrf()).param("name", "Food").param("type", "CHI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void requiresAuthenticationForEditForm() throws Exception {
        mockMvc.perform(get(EDIT_PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(categoryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EDIT_PATH, DELETE_PATH})
    void requiresCsrfForMutations(String path) throws Exception {
        mockMvc.perform(post(path).with(user(USERNAME)).param("name", "Food").param("type", "CHI"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void getRequestCannotDeleteCategory() throws Exception {
        mockMvc.perform(get(DELETE_PATH).with(user(USERNAME)))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(categoryService);
    }

    private MockHttpServletRequestBuilder editRequest(String name, String type) {
        MockHttpServletRequestBuilder request = post(EDIT_PATH)
                .with(user(USERNAME))
                .with(csrf())
                .param("name", name)
                .param("type", type);
        return request;
    }

    private CategoryDTO categoryDto() {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(CATEGORY_ID);
        dto.setName("Food");
        dto.setType("CHI");
        return dto;
    }
}
