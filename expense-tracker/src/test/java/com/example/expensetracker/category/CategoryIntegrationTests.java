package com.example.expensetracker.category;

import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryIntegrationTests {

    private static final String OTHER_CATEGORY_NAME = "Other user's private category";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    private User owner;
    private User otherUser;
    private Category otherCategory;

    @Autowired
    CategoryIntegrationTests(MockMvc mockMvc, UserRepository userRepository, CategoryRepository categoryRepository) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = userRepository.saveAndFlush(new User(
                "category-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner"));
        otherUser = userRepository.saveAndFlush(new User(
                "category-other-" + suffix + "@example.invalid", "unused-test-hash", "Other"));
        otherCategory = categoryRepository.saveAndFlush(
                new Category(otherUser.getId(), OTHER_CATEGORY_NAME, CategoryType.THU));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categories", "/categories/new"})
    void requiresAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void requiresAuthenticationForCreation() throws Exception {
        mockMvc.perform(post("/categories").with(csrf()).param("name", "Food").param("type", "CHI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(categoryRepository.findByUserId(owner.getId())).isEmpty();
    }

    @Test
    void rendersNewCategoryFormWithTypesAndCsrfToken() throws Exception {
        mockMvc.perform(get("/categories/new").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(view().name("category/form"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("value=\"CHI\"")))
                .andExpect(content().string(containsString("value=\"THU\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHI", "THU"})
    void createsCategoryForAuthenticatedUserAndIgnoresSubmittedOwnershipAndId(String type) throws Exception {
        mockMvc.perform(categoryRequest("  Ăn uống  ", type)
                        .param("userId", otherUser.getId().toString())
                        .param("id", otherCategory.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"));

        List<Category> categories = categoryRepository.findByUserId(owner.getId());
        assertThat(categories).hasSize(1);
        Category category = categories.getFirst();
        assertThat(category.getId()).isNotEqualTo(otherCategory.getId());
        assertThat(category.getUserId()).isEqualTo(owner.getId());
        assertThat(category.getName()).isEqualTo("Ăn uống");
        assertThat(category.getType().name()).isEqualTo(type);
        assertThat(categoryRepository.findByUserId(otherUser.getId()))
                .extracting(Category::getName)
                .containsExactly(OTHER_CATEGORY_NAME);
    }

    @Test
    void listsOnlyCurrentUsersDtosWithPaginationAndIgnoresRequestedUserId() throws Exception {
        categoryRepository.saveAndFlush(new Category(owner.getId(), "First category", CategoryType.CHI));
        categoryRepository.saveAndFlush(new Category(owner.getId(), "Second category", CategoryType.THU));

        MvcResult result = mockMvc.perform(get("/categories").with(user(owner.getEmail()))
                        .param("userId", otherUser.getId().toString())
                        .param("page", "0").param("size", "1").param("sort", "untrustedProperty,desc"))
                .andExpect(status().isOk())
                .andExpect(view().name("category/list"))
                .andExpect(content().string(containsString("First category")))
                .andExpect(content().string(not(containsString("Second category"))))
                .andExpect(content().string(not(containsString(OTHER_CATEGORY_NAME))))
                .andExpect(content().string(containsString("Trang sau")))
                .andReturn();

        Page<?> categories = categoryPage(result);
        assertThat(categories.getTotalElements()).isEqualTo(2);
        assertThat(categories.getTotalPages()).isEqualTo(2);
        assertThat(categories.getContent()).hasSize(1);
        assertThat(categories.getContent().getFirst()).isInstanceOf(CategoryDTO.class);

        mockMvc.perform(get("/categories").with(user(owner.getEmail())).param("page", "1").param("size", "1"))
                .andExpect(content().string(containsString("Second category")))
                .andExpect(content().string(not(containsString("First category"))))
                .andExpect(content().string(not(containsString(OTHER_CATEGORY_NAME))));
    }

    @Test
    void showsEmptyStateWithoutExposingAnotherUsersCategory() throws Exception {
        mockMvc.perform(get("/categories").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có danh mục nào.")))
                .andExpect(content().string(not(containsString(OTHER_CATEGORY_NAME))));
    }

    @Test
    void boundsPageSize() throws Exception {
        MvcResult result = mockMvc.perform(get("/categories").with(user(owner.getEmail())).param("size", "1000"))
                .andExpect(status().isOk())
                .andReturn();

        Page<?> categories = categoryPage(result);
        assertThat(categories.getSize()).isEqualTo(CategoryService.MAX_PAGE_SIZE);
    }

    @ParameterizedTest
    @MethodSource("invalidCategoryRequests")
    void rejectsInvalidInputWithoutSaving(String name, String type, String invalidField) throws Exception {
        mockMvc.perform(categoryRequest(name, type))
                .andExpect(status().isOk())
                .andExpect(view().name("category/form"))
                .andExpect(model().attributeHasFieldErrors(CategoryDTO.MODEL_ATTRIBUTE, invalidField))
                .andExpect(content().string(containsString("id=\"" + invalidField + "-error\"")));

        assertThat(categoryRepository.findByUserId(owner.getId())).isEmpty();
    }

    @Test
    void escapesCategoryNamesInList() throws Exception {
        mockMvc.perform(categoryRequest("<script>alert(1)</script>", "CHI"))
                .andExpect(redirectedUrl("/categories"));

        mockMvc.perform(get("/categories").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void requiresCsrfTokenForCreation() throws Exception {
        mockMvc.perform(post("/categories").with(user(owner.getEmail())).param("name", "Food").param("type", "CHI"))
                .andExpect(status().isForbidden());

        assertThat(categoryRepository.findByUserId(owner.getId())).isEmpty();
    }

    @Test
    void deniesStaleSessionWhenAccountNoLongerExists() throws Exception {
        String missingEmail = "missing-" + UUID.randomUUID() + "@example.invalid";

        mockMvc.perform(get("/categories").with(user(missingEmail)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/categories").with(user(missingEmail)).with(csrf())
                        .param("name", "Food").param("type", "CHI"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesDisabledAccountAndDoesNotCreateCategory() throws Exception {
        owner.setEnabled(false);
        userRepository.saveAndFlush(owner);

        mockMvc.perform(get("/categories").with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(categoryRequest("Food", "CHI"))
                .andExpect(status().isForbidden());

        assertThat(categoryRepository.findByUserId(owner.getId())).isEmpty();
    }

    private MockHttpServletRequestBuilder categoryRequest(String name, String type) {
        MockHttpServletRequestBuilder request = post("/categories")
                .with(user(owner.getEmail()))
                .with(csrf())
                .param("name", name)
                .param("type", type);
        return request;
    }

    private Page<?> categoryPage(MvcResult result) {
        assertThat(result.getModelAndView()).isNotNull();
        Object categories = result.getModelAndView().getModel().get("categories");
        assertThat(categories).isInstanceOf(Page.class);
        Page<?> categoryPage = (Page<?>) categories;
        return categoryPage;
    }

    private static Stream<Arguments> invalidCategoryRequests() {
        Stream<Arguments> arguments = Stream.of(
                Arguments.of("", "CHI", "name"),
                Arguments.of("   ", "THU", "name"),
                Arguments.of("x".repeat(101), "CHI", "name"),
                Arguments.of("Food", "", "type"),
                Arguments.of("Food", "   ", "type"),
                Arguments.of("Food", "OTHER", "type"),
                Arguments.of("Food", "chi", "type"));
        return arguments;
    }
}
