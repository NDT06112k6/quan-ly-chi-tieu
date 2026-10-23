package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetIntegrationTests {

    private final MockMvc mockMvc;
    private final BudgetService budgetService;
    private final EntityManager entityManager;
    private User owner;
    private User otherUser;
    private Category ownerCategory;
    private Category otherCategory;

    @Autowired
    BudgetIntegrationTests(MockMvc mockMvc, BudgetService budgetService, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.budgetService = budgetService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = new User("budget-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        otherUser = new User("budget-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(otherUser);
        ownerCategory = new Category(owner.getId(), "Owned budget category", CategoryType.CHI);
        otherCategory = new Category(otherUser.getId(), "Private foreign category", CategoryType.THU);
        entityManager.persist(ownerCategory);
        entityManager.persist(otherCategory);
        entityManager.flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/budgets", "/budgets/new", "/budgets/categories"})
    void requiresAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rendersOwnedCategoryDtosAndCurrentMonthWithCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/budgets/new").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(view().name("budget/form"))
                .andExpect(content().string(containsString("Owned budget category")))
                .andExpect(content().string(not(containsString("Private foreign category"))))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("bootstrap@5")))
                .andReturn();
        Map<String, Object> attributes = result.getModelAndView().getModel();
        BudgetDTO dto = (BudgetDTO) attributes.get(BudgetDTO.MODEL_ATTRIBUTE);
        assertThat(dto.getMonth()).isEqualTo(YearMonth.now().toString());
        assertThat((List<?>) attributes.get("categories")).allMatch(CategoryDTO.class::isInstance);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.10", "9999999999999.99"})
    void preservesExactMoneyAndIgnoresSubmittedOwnershipAndId(String amount) throws Exception {
        mockMvc.perform(validRequest().with(fieldValue("limitAmount", amount))
                        .param("id", "999999").param("userId", otherUser.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budgets"));
        List<Budget> budgets = budgetsFor(owner.getId());
        assertThat(budgets).hasSize(1);
        Budget budget = budgets.getFirst();
        assertThat(budget.getLimitAmount()).isEqualByComparingTo(amount);
        assertThat(budget.getMonth()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(budget.getCategoryId()).isEqualTo(ownerCategory.getId());
        assertThat(budget.getId()).isNotEqualTo(999999L);
        assertThat(budgetsFor(otherUser.getId())).isEmpty();
        assertThat(budgetService.getBudgetsByUserId(owner.getId()).getContent().getFirst().getMonth())
                .isEqualTo("2026-10");
    }

    @Test
    void updatesExistingMonthlyBudgetAndCreatesDifferentMonth() throws Exception {
        mockMvc.perform(validRequest()).andExpect(redirectedUrl("/budgets"));
        Long originalId = budgetsFor(owner.getId()).getFirst().getId();
        mockMvc.perform(validRequest().with(fieldValue("limitAmount", "78.90")))
                .andExpect(redirectedUrl("/budgets"));
        List<Budget> updated = budgetsFor(owner.getId());
        assertThat(updated).hasSize(1);
        assertThat(updated.getFirst().getId()).isEqualTo(originalId);
        assertThat(updated.getFirst().getLimitAmount()).isEqualByComparingTo("78.90");
        mockMvc.perform(validRequest().with(fieldValue("month", "2026-11")))
                .andExpect(redirectedUrl("/budgets"));
        assertThat(budgetsFor(owner.getId())).hasSize(2);
    }

    @Test
    void rejectsForeignCategoryWithoutChangingForeignBudget() throws Exception {
        Budget foreignBudget = new Budget(otherUser.getId(), otherCategory.getId(),
                LocalDate.of(2026, 10, 1), new BigDecimal("10.00"));
        entityManager.persist(foreignBudget);
        mockMvc.perform(validRequest().with(fieldValue("categoryId", otherCategory.getId().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_BUDGET_CATEGORY"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(content().string(not(containsString("Private foreign category"))));
        assertThat(budgetsFor(owner.getId())).isEmpty();
        assertThat(budgetsFor(otherUser.getId()).getFirst().getLimitAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsMissingCategory() throws Exception {
        mockMvc.perform(validRequest().with(fieldValue("categoryId", Long.toString(Long.MAX_VALUE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_BUDGET_CATEGORY"));
        assertThat(budgetsFor(owner.getId())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"month|", "month|2026-13", "month|2026-1", "month|2026-10-01",
            "month|0000-01", "month|0999-12", "limitAmount|", "limitAmount|0", "limitAmount|-1",
            "limitAmount|1.001", "limitAmount|10000000000000", "categoryId|", "categoryId|0"},
            delimiter = '|', emptyValue = "")
    void rejectsInvalidInputAndRepopulatesOwnedDropdown(String field, String value) throws Exception {
        mockMvc.perform(validRequest().with(fieldValue(field, value)))
                .andExpect(status().isOk())
                .andExpect(view().name("budget/form"))
                .andExpect(model().attributeHasFieldErrors(BudgetDTO.MODEL_ATTRIBUTE, field))
                .andExpect(content().string(containsString("Owned budget category")))
                .andExpect(content().string(not(containsString("Private foreign category"))));
        assertThat(budgetsFor(owner.getId())).isEmpty();
    }

    @Test
    void requiresCsrfOnPost() throws Exception {
        mockMvc.perform(post("/budgets").with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
        assertThat(budgetsFor(owner.getId())).isEmpty();
    }

    @Test
    void paginatesOnlyOwnedBudgetDtosAndSortsNewestMonthFirst() throws Exception {
        persistMonthlyBudgets(23);
        entityManager.persist(new Budget(otherUser.getId(), otherCategory.getId(),
                LocalDate.of(2099, 1, 1), new BigDecimal("999.00")));
        MvcResult result = mockMvc.perform(get("/budgets").with(user(owner.getEmail()))
                        .param("page", "1").param("size", "20").param("userId", otherUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("budget/list"))
                .andExpect(content().string(not(containsString("Private foreign category"))))
                .andExpect(content().string(containsString("page=0")))
                .andReturn();
        Page<?> page = (Page<?>) result.getModelAndView().getModel().get("budgets");
        assertThat(page.getTotalElements()).isEqualTo(23);
        assertThat(page.getContent()).hasSize(3).allMatch(BudgetDTO.class::isInstance);
        Page<BudgetDTO> firstPage = budgetService.getBudgetsByUserId(owner.getId());
        assertThat(firstPage.getSize()).isEqualTo(20);
        assertThat(firstPage.getContent().getFirst().getMonth()).isEqualTo("2027-11");
        assertThat(budgetService.getBudgetsByUserId(owner.getId(), PageRequest.of(0, 1000)).getSize())
                .isEqualTo(BudgetService.MAX_PAGE_SIZE);
    }

    @Test
    void paginatesDropdownAndRetainsSelectionBeyondFirstPageOnValidationError() throws Exception {
        Category selected = persistAdditionalCategories();
        mockMvc.perform(get("/budgets/categories").with(user(owner.getEmail())).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].id").value(selected.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));
        MvcResult result = mockMvc.perform(validRequest().with(fieldValue("categoryId", selected.getId().toString()))
                        .with(fieldValue("limitAmount", "0")))
                .andExpect(model().attributeHasFieldErrors(BudgetDTO.MODEL_ATTRIBUTE, "limitAmount"))
                .andExpect(content().string(containsString("Last owned category")))
                .andReturn();
        assertThat((List<?>) result.getModelAndView().getModel().get("categories")).hasSize(101);
    }

    @Test
    void rejectsNegativeDropdownPage() throws Exception {
        mockMvc.perform(get("/budgets/categories").with(user(owner.getEmail())).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void databaseRejectsDuplicateUserCategoryMonth() {
        LocalDate month = LocalDate.of(2026, 10, 1);
        entityManager.persist(new Budget(owner.getId(), ownerCategory.getId(), month, BigDecimal.ONE));
        assertThatThrownBy(() -> {
            entityManager.persist(new Budget(owner.getId(), ownerCategory.getId(), month, BigDecimal.TEN));
            entityManager.flush();
        }).isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    private RequestPostProcessor fieldValue(String field, String value) {
        RequestPostProcessor processor = request -> {
            request.setParameter(field, value);
            return request;
        };
        return processor;
    }

    private MockHttpServletRequestBuilder validRequest() {
        MockHttpServletRequestBuilder request = post("/budgets").with(user(owner.getEmail())).with(csrf());
        request.param("categoryId", ownerCategory.getId().toString());
        request.param("month", "2026-10");
        request.param("limitAmount", "12.34");
        return request;
    }

    private List<Budget> budgetsFor(Long userId) {
        entityManager.flush();
        List<Budget> budgets = entityManager.createQuery(
                        "SELECT budget FROM Budget budget WHERE budget.userId = :userId", Budget.class)
                .setParameter("userId", userId)
                .getResultList();
        return budgets;
    }

    private void persistMonthlyBudgets(int count) {
        YearMonth startingMonth = YearMonth.of(2026, 1);
        for (int index = 0; index < count; index++) {
            LocalDate month = startingMonth.plusMonths(index).atDay(1);
            entityManager.persist(new Budget(owner.getId(), ownerCategory.getId(), month, BigDecimal.TEN));
        }
        entityManager.flush();
    }

    private Category persistAdditionalCategories() {
        for (int index = 0; index < 100; index++) {
            entityManager.persist(new Category(owner.getId(), "Owned category " + index, CategoryType.CHI));
        }
        Category selected = new Category(owner.getId(), "Last owned category", CategoryType.CHI);
        entityManager.persist(selected);
        entityManager.flush();
        return selected;
    }
}
