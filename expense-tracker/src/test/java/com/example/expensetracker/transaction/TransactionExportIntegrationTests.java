package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionExportIntegrationTests {

    private static final String CSV_HEADER = "\uFEFFNgày,Danh mục,Số tiền,Ghi chú\r\n";
    private static final LocalDate EXPORT_DATE = LocalDate.of(2026, 10, 24);
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private User owner;
    private Category expenseCategory;
    private Category incomeCategory;
    private Category foreignCategory;

    @Autowired
    TransactionExportIntegrationTests(MockMvc mockMvc, UserRepository userRepository, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = userRepository.saveAndFlush(new User(
                "export-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner"));
        User otherUser = userRepository.saveAndFlush(new User(
                "export-other-" + suffix + "@example.invalid", "unused-test-hash", "Other"));
        expenseCategory = new Category(owner.getId(), "Ăn uống", CategoryType.CHI);
        incomeCategory = new Category(owner.getId(), "Lương", CategoryType.THU);
        foreignCategory = new Category(otherUser.getId(), "Foreign private category", CategoryType.CHI);
        entityManager.persist(expenseCategory);
        entityManager.persist(incomeCategory);
        entityManager.persist(foreignCategory);
        entityManager.flush();
    }

    @Test
    void returnsHeaderOnlyCsvWithExactAttachmentAndUtf8HeadersForEmptyAccount() throws Exception {
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(CSV_HEADER));
    }

    @Test
    void exportsOnlyCurrentOwnersIncomeAndExpensesAndIgnoresListFilters() throws Exception {
        persistTransaction(expenseCategory, EXPORT_DATE, "Lunch");
        persistTransaction(incomeCategory, EXPORT_DATE.minusMonths(1), "Salary");
        persistTransaction(foreignCategory, EXPORT_DATE, "Foreign private note");
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail()))
                        .param("userId", foreignCategory.getUserId().toString())
                        .param("keyword", "does-not-match").param("page", "99")
                        .param("categoryId", foreignCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(CSV_HEADER
                        + "2026-10-24,Ăn uống,12.34,Lunch\r\n"
                        + "2026-09-24,Lương,12.34,Salary\r\n"))
                .andExpect(content().string(not(containsString("Foreign private"))));
    }

    @Test
    void escapesVietnameseCategoryNamesAndMultilineNotes() throws Exception {
        expenseCategory.setName("Ăn, uống");
        persistTransaction(expenseCategory, EXPORT_DATE, "Bữa trưa, \"ngon\"\r\nDòng thứ hai");
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(CSV_HEADER
                        + "2026-10-24,\"Ăn, uống\",12.34,\"Bữa trưa, \"\"ngon\"\"\r\nDòng thứ hai\"\r\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.10", "9999999999999.99"})
    void preservesExactDecimalAmountsAndNullNotes(String amount) throws Exception {
        Transaction transaction = new Transaction(owner.getId(), expenseCategory.getId(),
                new BigDecimal(amount), EXPORT_DATE, null);
        entityManager.persist(transaction);
        entityManager.flush();
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(CSV_HEADER + "2026-10-24,Ăn uống," + amount + ",\r\n"));
    }

    @Test
    void exportsRowsBeyondTheFirstPageWithStableDateAndIdOrdering() throws Exception {
        for (int index = 0; index < 205; index++) {
            persistTransaction(expenseCategory, EXPORT_DATE, "Note " + index);
        }
        MvcResult result = mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String[] lines = result.getResponse().getContentAsString().split("\r\n");
        assertThat(lines).hasSize(206);
        assertThat(lines[1]).isEqualTo("2026-10-24,Ăn uống,12.34,Note 204");
        assertThat(lines[100]).isEqualTo("2026-10-24,Ăn uống,12.34,Note 105");
        assertThat(lines[205]).isEqualTo("2026-10-24,Ăn uống,12.34,Note 0");
    }

    @Test
    void writesFormulaLikeCategoryAndNoteAsText() throws Exception {
        expenseCategory.setName("=1+1");
        persistTransaction(expenseCategory, EXPORT_DATE, "@SUM(1,2)");
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(CSV_HEADER + "2026-10-24,\"'=1+1\",12.34,\"'@SUM(1,2)\"\r\n"));
    }

    @Test
    void deniesAnonymousDisabledAndMissingAccountsWithoutDownloadHeader() throws Exception {
        mockMvc.perform(get(TransactionController.EXPORT_PATH))
                .andExpect(redirectedUrl("/login"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        owner.setEnabled(false);
        userRepository.saveAndFlush(owner);
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user(owner.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        mockMvc.perform(get(TransactionController.EXPORT_PATH).with(user("missing-export@example.invalid")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void transactionListContainsExportLink() throws Exception {
        mockMvc.perform(get("/transactions").with(user(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/transactions/export\"")));
    }

    private void persistTransaction(Category category, LocalDate date, String note) {
        Transaction transaction = new Transaction(category.getUserId(), category.getId(), new BigDecimal("12.34"), date, note);
        entityManager.persist(transaction);
        entityManager.flush();
    }
}
