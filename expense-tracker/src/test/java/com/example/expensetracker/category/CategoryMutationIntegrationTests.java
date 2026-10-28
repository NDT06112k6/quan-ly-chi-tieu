package com.example.expensetracker.category;

import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.transaction.Transaction;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryMutationIntegrationTests {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 10, 1);
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    private final MockMvc mockMvc;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    private User owner;
    private User otherUser;
    private Category ownedCategory;
    private Category foreignCategory;

    @Autowired
    CategoryMutationIntegrationTests(MockMvc mockMvc, CategoryRepository categoryRepository, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void createAccountsAndCategories() {
        String suffix = UUID.randomUUID().toString();
        owner = new User("category-edit-owner-" + suffix + "@example.invalid", "unused-test-hash", "Owner");
        otherUser = new User("category-edit-other-" + suffix + "@example.invalid", "unused-test-hash", "Other");
        entityManager.persist(owner);
        entityManager.persist(otherUser);
        ownedCategory = categoryRepository.saveAndFlush(new Category(owner.getId(), "Food", CategoryType.CHI));
        foreignCategory = categoryRepository.saveAndFlush(new Category(otherUser.getId(), "Private", CategoryType.THU));
    }

    @Test
    void updatesOnlyOwnedCategoryAndIgnoresSubmittedOwnershipAndId() throws Exception {
        mockMvc.perform(post("/categories/{id}/edit", ownedCategory.getId())
                        .with(user(owner.getEmail())).with(csrf())
                        .param("name", "  Salary  ").param("type", "THU")
                        .param("id", foreignCategory.getId().toString())
                        .param("userId", otherUser.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("success", "Cập nhật danh mục thành công!"));

        Category updated = categoryRepository.findByIdAndUserId(ownedCategory.getId(), owner.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Salary");
        assertThat(updated.getType()).isEqualTo(CategoryType.THU);
        assertThat(updated.getUserId()).isEqualTo(owner.getId());
        Category unchanged = categoryRepository.findByIdAndUserId(foreignCategory.getId(), otherUser.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Private");
    }

    @Test
    void deletesUnusedCategoryEvenWhenAnotherUsersCategoryHasTransactions() throws Exception {
        Transaction transaction = new Transaction(otherUser.getId(), foreignCategory.getId(), AMOUNT,
                TRANSACTION_DATE, "Private transaction");
        entityManager.persist(transaction);
        entityManager.flush();

        mockMvc.perform(post("/categories/{id}/delete", ownedCategory.getId())
                        .with(user(owner.getEmail())).with(csrf()).param("userId", otherUser.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("success", "Xóa danh mục thành công!"));

        assertThat(categoryRepository.findByIdAndUserId(ownedCategory.getId(), owner.getId())).isEmpty();
        assertThat(categoryRepository.findByIdAndUserId(foreignCategory.getId(), otherUser.getId())).isPresent();
    }

    @Test
    void keepsCategoryAndTransactionWhenDeletionIsRejected() throws Exception {
        Transaction transaction = new Transaction(owner.getId(), ownedCategory.getId(), AMOUNT,
                TRANSACTION_DATE, "Lunch");
        entityManager.persist(transaction);
        entityManager.flush();

        mockMvc.perform(post("/categories/{id}/delete", ownedCategory.getId())
                        .with(user(owner.getEmail())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("error", new CategoryInUseException().getMessage()))
                .andExpect(flash().attributeCount(1));

        assertThat(categoryRepository.findByIdAndUserId(ownedCategory.getId(), owner.getId())).isPresent();
        long transactionCount = entityManager.createQuery("""
                SELECT COUNT(transaction) FROM Transaction transaction
                WHERE transaction.categoryId = :categoryId AND transaction.userId = :userId
                """, Long.class)
                .setParameter("categoryId", ownedCategory.getId())
                .setParameter("userId", owner.getId())
                .getSingleResult();
        assertThat(transactionCount).isEqualTo(1);
    }

    @Test
    void keepsCategoryAndBudgetWhenDeletionIsRejected() throws Exception {
        Budget budget = new Budget(
                owner.getId(),
                ownedCategory.getId(),
                BudgetPeriod.containing(BudgetType.MONTHLY, TRANSACTION_DATE),
                AMOUNT);
        entityManager.persist(budget);
        entityManager.flush();

        mockMvc.perform(post("/categories/{id}/delete", ownedCategory.getId())
                        .with(user(owner.getEmail())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("error", new CategoryInUseException().getMessage()));

        assertThat(categoryRepository.findByIdAndUserId(ownedCategory.getId(), owner.getId())).isPresent();
        long budgetCount = entityManager.createQuery("""
                SELECT COUNT(budget) FROM Budget budget
                WHERE budget.categoryId = :categoryId AND budget.userId = :userId
                """, Long.class)
                .setParameter("categoryId", ownedCategory.getId())
                .setParameter("userId", owner.getId())
                .getSingleResult();
        assertThat(budgetCount).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "delete"})
    void deniesMutatingForeignCategory(String action) throws Exception {
        mockMvc.perform(post("/categories/{id}/{action}", foreignCategory.getId(), action)
                        .with(user(owner.getEmail())).with(csrf())
                        .param("name", "Changed").param("type", "CHI")
                        .param("userId", otherUser.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(flash().attributeCount(0));

        entityManager.clear();
        Category unchanged = categoryRepository.findByIdAndUserId(foreignCategory.getId(), otherUser.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Private");
    }

    @Test
    void deniesReadingForeignEditForm() throws Exception {
        mockMvc.perform(get("/categories/{id}/edit", foreignCategory.getId()).with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "delete"})
    void deniesMutatingMissingCategory(String action) throws Exception {
        mockMvc.perform(post("/categories/{id}/{action}", Long.MAX_VALUE, action)
                        .with(user(owner.getEmail())).with(csrf()).param("name", "Food").param("type", "CHI"))
                .andExpect(status().isForbidden())
                .andExpect(flash().attributeCount(0));
    }
}
