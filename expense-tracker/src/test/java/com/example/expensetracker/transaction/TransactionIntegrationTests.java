package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionIntegrationTests {

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    private User owner;
    private User otherUser;
    private Category ownerCategory;
    private Category otherCategory;

    @Autowired
    TransactionIntegrationTests(MockMvc mockMvc, UserRepository userRepository, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = userRepository.saveAndFlush(new User(
                "transaction-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner"));
        otherUser = userRepository.saveAndFlush(new User(
                "transaction-other-" + suffix + "@example.invalid", "unused-test-hash", "Other"));
        ownerCategory = new Category(owner.getId(), "My category", CategoryType.CHI);
        otherCategory = new Category(otherUser.getId(), "Foreign private category", CategoryType.THU);
        entityManager.persist(ownerCategory);
        entityManager.persist(otherCategory);
        entityManager.flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/transactions", "/transactions/new", "/transactions/categories"})
    void requiresAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rendersFormWithOwnedCategoryDtosAndCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/transactions/new").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction/form"))
                .andExpect(content().string(containsString("My category")))
                .andExpect(content().string(containsString(
                        "<option value=\"" + ownerCategory.getId() + "\">My category</option>")))
                .andExpect(content().string(not(containsString("Foreign private category"))))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("inputmode=\"numeric\"")))
                .andExpect(content().string(containsString("id=\"realAmount\"")))
                .andExpect(content().string(containsString("/js/transaction-amount.js")))
                .andExpect(content().string(containsString("data-amount=\"10000000\"")))
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        Object categories = result.getModelAndView().getModel().get("categories");
        assertThat(categories).isInstanceOf(List.class);
        assertThat((List<?>) categories).hasSize(1);
        assertThat(((List<?>) categories).getFirst()).isInstanceOf(CategoryDTO.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.10", "9999999999999.99"})
    void preservesExactMoneyAndIgnoresSubmittedOwnerAndTransactionId(String amount) throws Exception {
        Transaction foreignTransaction = new Transaction(otherUser.getId(), otherCategory.getId(),
                new BigDecimal("1.00"), LocalDate.of(2026, 10, 23), "Foreign transaction");
        entityManager.persist(foreignTransaction);
        Map<String, String> fields = validFields();
        fields.put("amount", amount);
        fields.put("userId", otherUser.getId().toString());
        fields.put("id", foreignTransaction.getId().toString());

        mockMvc.perform(transactionRequest(fields))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));

        List<Transaction> transactions = transactionsFor(owner.getId());
        assertThat(transactions).hasSize(1);
        Transaction transaction = transactions.getFirst();
        assertThat(transaction.getAmount()).isEqualByComparingTo(amount);
        assertThat(transaction.getOccurredOn()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(transaction.getNote()).isEqualTo("Transaction test note");
        assertThat(transaction.getCategoryId()).isEqualTo(ownerCategory.getId());
        assertThat(transaction.getId()).isNotEqualTo(foreignTransaction.getId());
        assertThat(transactionsFor(otherUser.getId())).hasSize(1);
    }

    @Test
    void rejectsAnotherUsersCategoryBeforeSaving() throws Exception {
        Map<String, String> fields = validFields();
        fields.put("categoryId", otherCategory.getId().toString());

        mockMvc.perform(transactionRequest(fields))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_CATEGORY"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(content().string(not(containsString("Foreign private category"))));

        assertThat(transactionsFor(owner.getId())).isEmpty();
        assertThat(transactionsFor(otherUser.getId())).isEmpty();
    }

    @Test
    void colorsPositiveAmountsByCategoryTypeRatherThanAmountSign() throws Exception {
        Category incomeCategory = new Category(owner.getId(), "Salary", CategoryType.THU);
        entityManager.persist(incomeCategory);
        LocalDate occurredOn = LocalDate.of(2026, 10, 23);
        BigDecimal amount = new BigDecimal("10.00");
        Transaction expense = new Transaction(owner.getId(), ownerCategory.getId(), amount, occurredOn, null);
        Transaction income = new Transaction(owner.getId(), incomeCategory.getId(), amount, occurredOn, null);
        entityManager.persist(expense);
        entityManager.persist(income);
        entityManager.flush();

        mockMvc.perform(get("/transactions").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*<td[^>]*>My category</td>\\s*<td[^>]*class=\"[^\"]*text-danger[^\"]*\"[^>]*>10 ₫</td>.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<td[^>]*>Salary</td>\\s*<td[^>]*class=\"[^\"]*text-success[^\"]*\"[^>]*>10 ₫</td>.*")));
    }

    @Test
    void displaysGroupedCurrencyWhilePreservingTransactionAmount() throws Exception {
        Transaction transaction = new Transaction(owner.getId(), ownerCategory.getId(),
                new BigDecimal("50000.75"), LocalDate.of(2026, 10, 1), "Grouped currency");
        entityManager.persist(transaction);
        entityManager.flush();

        mockMvc.perform(get("/transactions").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("50,001 ₫")));

        assertThat(transaction.getAmount()).isEqualByComparingTo("50000.75");
    }

    @Test
    void rejectsNonexistentCategoryBeforeSaving() throws Exception {
        Map<String, String> fields = validFields();
        fields.put("categoryId", Long.toString(Long.MAX_VALUE));

        mockMvc.perform(transactionRequest(fields))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_CATEGORY"));

        assertThat(transactionsFor(owner.getId())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rendersFieldErrorsAndRestoresOwnedCategoriesWithoutSaving(String fieldName, String value) throws Exception {
        Map<String, String> fields = validFields();
        fields.put(fieldName, value);

        mockMvc.perform(transactionRequest(fields))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction/form"))
                .andExpect(model().attributeHasFieldErrors(TransactionDTO.MODEL_ATTRIBUTE, fieldName))
                .andExpect(content().string(containsString("My category")))
                .andExpect(content().string(not(containsString("Foreign private category"))));

        assertThat(transactionsFor(owner.getId())).isEmpty();
    }

    @Test
    void paginatesOnlyCurrentUsersDtosAndShowsCategoryName() throws Exception {
        createTransactionsForPagination();

        MvcResult result = mockMvc.perform(get("/transactions").with(user(owner.getEmail()))
                        .param("userId", otherUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction/list"))
                .andExpect(content().string(containsString("My category")))
                .andExpect(content().string(not(containsString("Foreign transaction"))))
                .andExpect(content().string(not(containsString("Foreign private category"))))
                .andExpect(content().string(containsString("Trang sau")))
                .andReturn();

        Page<?> transactions = transactionPage(result);
        assertThat(transactions.getNumber()).isZero();
        assertThat(transactions.getTotalElements()).isEqualTo(21);
        assertThat(transactions.getContent()).hasSize(TransactionService.DEFAULT_PAGE_SIZE);
        assertThat(transactions.getContent().getFirst()).isInstanceOf(TransactionDTO.class);
        TransactionDTO first = (TransactionDTO) transactions.getContent().getFirst();
        assertThat(first.getNote()).isEqualTo("Owner transaction 20");

        MvcResult secondResult = mockMvc.perform(get("/transactions").with(user(owner.getEmail())).param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(transactionPage(secondResult).getContent()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "not-a-page"})
    void rejectsInvalidPageWithStructuredError(String page) throws Exception {
        mockMvc.perform(get("/transactions").with(user(owner.getEmail())).param("page", page))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void loadsCategoriesInPagesAndRetainsSelectionBeyondInitialPageOnValidationError() throws Exception {
        Category lastCategory = createAdditionalCategories();

        mockMvc.perform(get("/transactions/new").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tải thêm danh mục")))
                .andExpect(content().string(not(containsString(lastCategory.getName()))));
        mockMvc.perform(get("/transactions/categories").with(user(owner.getEmail())).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(lastCategory.getName()));

        Map<String, String> fields = validFields();
        fields.put("amount", "");
        fields.put("categoryId", lastCategory.getId().toString());
        mockMvc.perform(transactionRequest(fields))
                .andExpect(view().name("transaction/form"))
                .andExpect(model().attributeHasFieldErrors(TransactionDTO.MODEL_ATTRIBUTE, "amount"))
                .andExpect(content().string(containsString(lastCategory.getName())))
                .andExpect(content().string(containsString("value=\"" + lastCategory.getId() + "\" selected=\"selected\"")));
    }

    @Test
    void rejectsNegativeCategoryPage() throws Exception {
        mockMvc.perform(get("/transactions/categories").with(user(owner.getEmail())).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void showsEmptyStateAndCategoryCreationLinkWhenUserHasNoCategories() throws Exception {
        entityManager.remove(ownerCategory);
        entityManager.flush();

        mockMvc.perform(get("/transactions").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có giao dịch nào.")));
        mockMvc.perform(get("/transactions/new").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/categories/new\"")))
                .andExpect(content().string(containsString("disabled=\"disabled\"")));
    }

    @Test
    void escapesNotesInTransactionList() throws Exception {
        Map<String, String> fields = validFields();
        fields.put("note", "<script>alert(1)</script>");
        mockMvc.perform(transactionRequest(fields)).andExpect(redirectedUrl("/transactions"));

        mockMvc.perform(get("/transactions").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void requiresCsrfAndAuthenticationForCreation() throws Exception {
        mockMvc.perform(post("/transactions").with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/transactions").with(csrf()))
                .andExpect(redirectedUrl("/login"));

        assertThat(transactionsFor(owner.getId())).isEmpty();
    }

    private Map<String, String> validFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("amount", "123.45");
        fields.put("occurredOn", "2026-10-23");
        fields.put("categoryId", ownerCategory.getId().toString());
        fields.put("note", "Transaction test note");
        return fields;
    }

    private MockHttpServletRequestBuilder transactionRequest(Map<String, String> fields) {
        MockHttpServletRequestBuilder request = post("/transactions")
                .with(user(owner.getEmail()))
                .with(csrf());
        for (Map.Entry<String, String> field : fields.entrySet()) {
            request.param(field.getKey(), field.getValue());
        }
        return request;
    }

    private List<Transaction> transactionsFor(Long userId) {
        entityManager.flush();
        List<Transaction> transactions = entityManager.createQuery(
                        "SELECT entity FROM Transaction entity WHERE entity.userId = :userId", Transaction.class)
                .setParameter("userId", userId)
                .getResultList();
        return transactions;
    }

    private Page<?> transactionPage(MvcResult result) {
        assertThat(result.getModelAndView()).isNotNull();
        Object transactions = result.getModelAndView().getModel().get("transactions");
        assertThat(transactions).isInstanceOf(Page.class);
        Page<?> page = (Page<?>) transactions;
        return page;
    }

    private void createTransactionsForPagination() {
        for (int index = 0; index < 21; index++) {
            Transaction transaction = new Transaction(owner.getId(), ownerCategory.getId(),
                    new BigDecimal("1.00"), LocalDate.of(2026, 10, 23), "Owner transaction " + index);
            entityManager.persist(transaction);
        }
        Transaction foreignTransaction = new Transaction(otherUser.getId(), otherCategory.getId(),
                new BigDecimal("2.00"), LocalDate.of(2026, 10, 23), "Foreign transaction");
        entityManager.persist(foreignTransaction);
        entityManager.flush();
    }

    private Category createAdditionalCategories() {
        Category lastCategory = ownerCategory;
        for (int index = 0; index < 100; index++) {
            lastCategory = new Category(owner.getId(), "Extra category " + index, CategoryType.CHI);
            entityManager.persist(lastCategory);
        }
        entityManager.flush();
        return lastCategory;
    }

    private static Stream<Arguments> invalidFields() {
        Stream<Arguments> fields = Stream.of(
                Arguments.of("amount", ""),
                Arguments.of("amount", "0"),
                Arguments.of("amount", "-1.00"),
                Arguments.of("amount", "0.001"),
                Arguments.of("amount", "10000000000000.00"),
                Arguments.of("amount", "not-a-number"),
                Arguments.of("occurredOn", ""),
                Arguments.of("occurredOn", "2026-02-30"),
                Arguments.of("categoryId", ""),
                Arguments.of("categoryId", "-1"),
                Arguments.of("note", "x".repeat(1001)));
        return fields;
    }
}
