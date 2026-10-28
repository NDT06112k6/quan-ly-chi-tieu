package com.example.expensetracker;

import com.example.expensetracker.budget.BudgetPeriod;
import com.example.expensetracker.budget.BudgetDTO;
import com.example.expensetracker.budget.BudgetType;
import com.example.expensetracker.budget.Budget;
import com.example.expensetracker.budget.BudgetRepository;
import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.transaction.Transaction;
import com.example.expensetracker.transaction.TransactionRepository;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.test.database.replace=none")
class RepositoryIsolationTests {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final EntityManager entityManager;

    private User owner;
    private User otherUser;
    private Category ownerCategory;
    private Category otherCategory;

    @Autowired
    RepositoryIsolationTests(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String passwordHash = passwordEncoder.encode("repository-test-password");
        String suffix = UUID.randomUUID().toString();

        owner = userRepository.saveAndFlush(
                new User("owner-" + suffix + "@example.invalid", passwordHash, "Owner"));
        otherUser = userRepository.saveAndFlush(
                new User("other-" + suffix + "@example.invalid", passwordHash, "Other"));
        ownerCategory = categoryRepository.saveAndFlush(
                new Category(owner.getId(), "Food", CategoryType.CHI));
        otherCategory = categoryRepository.saveAndFlush(
                new Category(otherUser.getId(), "Salary", CategoryType.THU));
    }

    @Test
    void findsUsersByEmail() {
        assertThat(userRepository.findByEmail(owner.getEmail()))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(owner.getId());
        assertThat(userRepository.existsByEmail(otherUser.getEmail())).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.invalid")).isFalse();
    }

    @Test
    void scopesCategoryReadsUpdatesAndDeletesToOwner() {
        assertThat(categoryRepository.findAllByUserId(owner.getId()))
                .extracting(Category::getId)
                .containsExactly(ownerCategory.getId());
        assertThat(categoryRepository.findByIdAndUserId(otherCategory.getId(), owner.getId())).isEmpty();
        assertThat(categoryRepository.existsByIdAndUserId(otherCategory.getId(), owner.getId())).isFalse();
        assertThat(categoryRepository.deleteByIdAndUserId(otherCategory.getId(), owner.getId())).isZero();

        assertThat(categoryRepository.updateByIdAndUserId(
                otherCategory.getId(), owner.getId(), "Forbidden", CategoryType.CHI)).isZero();
        assertThat(categoryRepository.updateByIdAndUserId(
                ownerCategory.getId(), owner.getId(), "Groceries", CategoryType.CHI)).isEqualTo(1);

        Category updated = categoryRepository.findByIdAndUserId(ownerCategory.getId(), owner.getId())
                .orElseThrow();
        assertThat(updated.getName()).isEqualTo("Groceries");
        assertThat(categoryRepository.deleteByIdAndUserId(ownerCategory.getId(), owner.getId())).isEqualTo(1);
        categoryRepository.flush();
        entityManager.clear();

        assertThat(categoryRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(categoryRepository.existsByIdAndUserId(otherCategory.getId(), otherUser.getId())).isTrue();
    }

    @Test
    void scopesTransactionsAndPreservesExactMoney() {
        BigDecimal amount = new BigDecimal("9999999999999.99");
        LocalDate occurredOn = LocalDate.of(2026, 10, 23);
        Transaction ownTransaction = transactionRepository.saveAndFlush(new Transaction(
                owner.getId(), ownerCategory.getId(), amount, occurredOn, "Exact amount"));
        Transaction otherTransaction = transactionRepository.saveAndFlush(new Transaction(
                otherUser.getId(), otherCategory.getId(), new BigDecimal("0.10"), occurredOn, null));
        entityManager.clear();

        assertThat(transactionRepository.findAllByUserId(owner.getId()))
                .extracting(Transaction::getId)
                .containsExactly(ownTransaction.getId());
        assertThat(transactionRepository.findByIdAndUserId(otherTransaction.getId(), owner.getId())).isEmpty();
        assertThat(transactionRepository.existsByIdAndUserId(otherTransaction.getId(), owner.getId())).isFalse();
        assertThat(transactionRepository.deleteByIdAndUserId(otherTransaction.getId(), owner.getId())).isZero();

        Transaction reloaded = transactionRepository.findByIdAndUserId(ownTransaction.getId(), owner.getId())
                .orElseThrow();
        assertThat(reloaded.getAmount()).isEqualByComparingTo(amount);
        assertThat(reloaded.getCreatedAt()).isNotNull();

        assertThat(transactionRepository.updateByIdAndUserId(
                otherTransaction.getId(), owner.getId(), ownerCategory.getId(),
                new BigDecimal("0.01"), occurredOn, null)).isZero();
        assertThat(transactionRepository.updateByIdAndUserId(
                ownTransaction.getId(), owner.getId(), ownerCategory.getId(),
                new BigDecimal("0.01"), occurredOn, null)).isEqualTo(1);

        Transaction updated = transactionRepository.findByIdAndUserId(ownTransaction.getId(), owner.getId())
                .orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo("0.01");
        assertThat(transactionRepository.deleteByIdAndUserId(ownTransaction.getId(), owner.getId())).isEqualTo(1);
        transactionRepository.flush();
        entityManager.clear();

        assertThat(transactionRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(transactionRepository.existsByIdAndUserId(otherTransaction.getId(), otherUser.getId())).isTrue();
    }

    @Test
    void scopesBudgetsAndPreservesExactLimit() {
        BigDecimal limit = new BigDecimal("9999999999999.99");
        LocalDate month = LocalDate.of(2026, 10, 1);
        Budget ownBudget = budgetRepository.saveAndFlush(new Budget(
                owner.getId(),
                ownerCategory.getId(),
                BudgetPeriod.containing(BudgetType.MONTHLY, month),
                limit));
        Budget otherBudget = budgetRepository.saveAndFlush(new Budget(
                otherUser.getId(),
                otherCategory.getId(),
                BudgetPeriod.containing(BudgetType.MONTHLY, month),
                new BigDecimal("0.10")));
        entityManager.clear();

        assertThat(budgetRepository.findAllByUserId(owner.getId()))
                .extracting(Budget::getId)
                .containsExactly(ownBudget.getId());
        assertThat(budgetRepository.findByIdAndUserId(otherBudget.getId(), owner.getId())).isEmpty();
        assertThat(budgetRepository.existsByIdAndUserId(otherBudget.getId(), owner.getId())).isFalse();
        assertThat(budgetRepository.deleteByIdAndUserId(otherBudget.getId(), owner.getId())).isZero();

        Budget reloaded = budgetRepository.findByIdAndUserId(ownBudget.getId(), owner.getId())
                .orElseThrow();
        assertThat(reloaded.getLimitAmount()).isEqualByComparingTo(limit);
        assertThat(reloaded.getPeriod()).isEqualTo("2026-10");
        assertThat(reloaded.getType()).isEqualTo(BudgetType.MONTHLY);

        BudgetDTO update = new BudgetDTO();
        update.setCategoryId(ownerCategory.getId());
        update.setPeriod("2026-10");
        update.setType(BudgetType.MONTHLY);
        update.setLimitAmount(new BigDecimal("0.01"));
        assertThat(budgetRepository.updateByIdAndUserId(
                otherBudget.getId(), owner.getId(), update)).isZero();
        assertThat(budgetRepository.updateByIdAndUserId(
                ownBudget.getId(), owner.getId(), update)).isEqualTo(1);

        Budget updated = budgetRepository.findByIdAndUserId(ownBudget.getId(), owner.getId())
                .orElseThrow();
        assertThat(updated.getLimitAmount()).isEqualByComparingTo("0.01");
        assertThat(budgetRepository.deleteByIdAndUserId(ownBudget.getId(), owner.getId())).isEqualTo(1);
        budgetRepository.flush();
        entityManager.clear();

        assertThat(budgetRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(budgetRepository.existsByIdAndUserId(otherBudget.getId(), otherUser.getId())).isTrue();
    }

    @Test
    void rejectsTransactionWithAnotherUsersCategory() {
        Transaction transaction = new Transaction(
                owner.getId(), otherCategory.getId(), new BigDecimal("12.34"), LocalDate.of(2026, 10, 23), null);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBudgetWithAnotherUsersCategory() {
        Budget budget = new Budget(
                owner.getId(),
                otherCategory.getId(),
                BudgetPeriod.containing(BudgetType.MONTHLY, LocalDate.of(2026, 10, 1)),
                new BigDecimal("12.34"));

        assertThatThrownBy(() -> budgetRepository.saveAndFlush(budget))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMalformedMonthlyBudgetPeriod() {
        assertThatThrownBy(() -> new Budget(
                owner.getId(),
                ownerCategory.getId(),
                new BudgetPeriod(BudgetType.MONTHLY, "2026-10-23"),
                new BigDecimal("12.34")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
