package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionSearchIntegrationTests {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 10, 31);
    private final MockMvc mockMvc;
    private final TransactionService transactionService;
    private final EntityManager entityManager;
    private User owner;
    private Category foodCategory;
    private Category transportCategory;
    private Category foreignCategory;

    @Autowired
    TransactionSearchIntegrationTests(MockMvc mockMvc, TransactionService transactionService, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.transactionService = transactionService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = new User("search-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        User otherUser = new User("search-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(otherUser);
        foodCategory = new Category(owner.getId(), "Food", CategoryType.CHI);
        transportCategory = new Category(owner.getId(), "Transport", CategoryType.CHI);
        foreignCategory = new Category(otherUser.getId(), "Foreign private category", CategoryType.CHI);
        entityManager.persist(foodCategory);
        entityManager.persist(transportCategory);
        entityManager.persist(foreignCategory);
        entityManager.flush();
    }

    @Test
    void searchesNoteSubstringAndRestrictsEveryResultToCurrentOwner() throws Exception {
        persistTransaction(foodCategory, START_DATE, "Morning coffee and bread");
        persistTransaction(foodCategory, START_DATE, "Lunch");
        persistTransaction(foodCategory, START_DATE, null);
        persistTransaction(foreignCategory, START_DATE, "Private coffee transaction");
        MvcResult result = mockMvc.perform(searchRequest().param("keyword", "  coffee  ")
                        .param("userId", foreignCategory.getUserId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction/list"))
                .andExpect(content().string(not(containsString("Private coffee transaction"))))
                .andExpect(content().string(not(containsString("Foreign private category"))))
                .andReturn();
        assertThat(notes(result)).containsExactly("Morning coffee and bread");
        assertThat(filters(result).getKeyword()).isEqualTo("coffee");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "' OR 1=1 --"})
    void treatsSpecialCharactersAsLiteralTextRatherThanPatternsOrSql(String keyword) throws Exception {
        persistTransaction(foodCategory, START_DATE, "Literal " + keyword + " note");
        persistTransaction(foodCategory, START_DATE, "Unrelated note");
        persistTransaction(foreignCategory, START_DATE, "Foreign " + keyword + " note");
        MvcResult result = mockMvc.perform(searchRequest().param("keyword", keyword))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(notes(result)).containsExactly("Literal " + keyword + " note");
    }

    @Test
    void appliesAllFiltersTogetherWithInclusiveDateBounds() throws Exception {
        persistTransaction(foodCategory, START_DATE, "coffee at start");
        persistTransaction(foodCategory, END_DATE, "coffee at end");
        persistTransaction(foodCategory, START_DATE.minusDays(1), "coffee too early");
        persistTransaction(foodCategory, END_DATE.plusDays(1), "coffee too late");
        persistTransaction(foodCategory, START_DATE, "tea");
        persistTransaction(transportCategory, START_DATE, "coffee wrong category");
        persistTransaction(foreignCategory, START_DATE, "coffee foreign");
        MvcResult result = mockMvc.perform(searchRequest().param("keyword", "coffee")
                        .param("categoryId", foodCategory.getId().toString())
                        .param("startDate", START_DATE.toString()).param("endDate", END_DATE.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(notes(result)).containsExactly("coffee at end", "coffee at start");
    }

    @ParameterizedTest
    @CsvSource({"startDate,2026-10-31,newer", "endDate,2026-10-01,older"})
    void supportsOneSidedDateRanges(String parameter, String date, String expectedNote) throws Exception {
        persistTransaction(foodCategory, START_DATE, "older");
        persistTransaction(foodCategory, END_DATE, "newer");
        MvcResult result = mockMvc.perform(searchRequest().param(parameter, date))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(notes(result)).containsExactly(expectedNote);
    }

    @Test
    void optionalEmptyFiltersIncludeNotesThatAreNull() throws Exception {
        persistTransaction(foodCategory, START_DATE, null);
        persistTransaction(transportCategory, END_DATE, "Bus fare");
        MvcResult result = mockMvc.perform(searchRequest().param("keyword", "   ")
                        .param("categoryId", "").param("startDate", "").param("endDate", ""))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(notes(result)).containsExactly("Bus fare", null);
        assertThat(filters(result).isActive()).isFalse();
    }

    @Test
    void foreignCategoryFilterReturnsNoRecordsAndDoesNotRevealTheCategory() throws Exception {
        persistTransaction(foodCategory, START_DATE, "Owned note");
        persistTransaction(foreignCategory, START_DATE, "Foreign note");
        MvcResult result = mockMvc.perform(searchRequest().param("categoryId", foreignCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có giao dịch phù hợp với bộ lọc.")))
                .andExpect(content().string(not(containsString("Foreign private category"))))
                .andReturn();
        assertThat(notes(result)).isEmpty();
    }

    @Test
    void retainsAllFiltersAndEncodedKeywordInPaginationLinks() throws Exception {
        for (int index = 0; index < 21; index++) {
            persistTransaction(foodCategory, START_DATE, "coffee & tea " + index);
        }
        MockHttpServletRequestBuilder request = searchRequest().param("keyword", "coffee & tea")
                .param("categoryId", foodCategory.getId().toString())
                .param("startDate", START_DATE.toString()).param("endDate", END_DATE.toString());
        MvcResult firstPage = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*<form[^>]*method=\"get\"\\s+action=\"/transactions\"[^>]*>.*")))
                .andExpect(content().string(containsString("keyword=coffee%20%26%20tea")))
                .andExpect(content().string(containsString("categoryId=" + foodCategory.getId())))
                .andExpect(content().string(containsString("startDate=2026-10-01")))
                .andExpect(content().string(containsString("endDate=2026-10-31")))
                .andExpect(content().string(containsString("value=\"coffee &amp; tea\"")))
                .andReturn();
        assertThat(notes(firstPage)).hasSize(20);
        assertThat(notes(firstPage).getFirst()).isEqualTo("coffee & tea 20");
        MvcResult secondPage = mockMvc.perform(request.param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(notes(secondPage)).containsExactly("coffee & tea 0");
    }

    @ParameterizedTest
    @CsvSource({"categoryId,0", "categoryId,-1", "categoryId,not-a-category",
            "startDate,not-a-date", "startDate,2026-02-30", "startDate,0000-01-01",
            "endDate,10000-01-01"})
    void rejectsInvalidFiltersWithStructuredBadRequest(String field, String value) throws Exception {
        mockMvc.perform(searchRequest().param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void rejectsReversedDateRangeAndOverlongKeyword() throws Exception {
        mockMvc.perform(searchRequest().param("startDate", "2026-10-31").param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(searchRequest().param("keyword", "x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void serviceValidatesFiltersAndCapsResultPageSize() {
        TransactionFilterDTO filters = new TransactionFilterDTO();
        filters.setCategoryId(-1L);
        assertThatThrownBy(() -> transactionService.getTransactionsByUserId(owner.getId(), filters, PageRequest.of(0, 20)))
                .isInstanceOf(ConstraintViolationException.class);
        filters.setCategoryId(null);
        Page<TransactionDTO> transactions = transactionService.getTransactionsByUserId(
                owner.getId(), filters, PageRequest.of(0, 1000));
        assertThat(transactions.getSize()).isEqualTo(TransactionService.MAX_PAGE_SIZE);
    }

    @Test
    void keepsSelectedCategoryBeyondFirstDropdownPage() throws Exception {
        Category selectedCategory = foodCategory;
        for (int index = 0; index < 100; index++) {
            selectedCategory = new Category(owner.getId(), "Extra category " + index, CategoryType.CHI);
            entityManager.persist(selectedCategory);
        }
        persistTransaction(selectedCategory, START_DATE, "Selected category transaction");
        MvcResult result = mockMvc.perform(searchRequest().param("categoryId", selectedCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tải thêm danh mục")))
                .andExpect(content().string(containsString(
                        "value=\"" + selectedCategory.getId() + "\" selected=\"selected\"")))
                .andReturn();
        assertThat(notes(result)).containsExactly("Selected category transaction");
    }

    private MockHttpServletRequestBuilder searchRequest() {
        MockHttpServletRequestBuilder request = get("/transactions").with(user(owner.getEmail()));
        return request;
    }

    private void persistTransaction(Category category, LocalDate date, String note) {
        Transaction transaction = new Transaction(category.getUserId(), category.getId(), new BigDecimal("12.34"), date, note);
        entityManager.persist(transaction);
        entityManager.flush();
    }

    private List<String> notes(MvcResult result) {
        Page<?> transactions = (Page<?>) result.getModelAndView().getModel().get("transactions");
        List<String> notes = transactions.getContent().stream()
                .map(TransactionDTO.class::cast)
                .map(TransactionDTO::getNote)
                .toList();
        return notes;
    }

    private TransactionFilterDTO filters(MvcResult result) {
        TransactionFilterDTO filters = (TransactionFilterDTO) result.getModelAndView().getModel().get("filters");
        return filters;
    }
}
