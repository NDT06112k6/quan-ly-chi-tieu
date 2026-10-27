package com.example.expensetracker.transaction;

import com.example.expensetracker.home.CategoryExpenseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Use methods with userId for user-owned data, including lookups before updates.
 * The caller must supply the authenticated user's ID.
 * Inherited methods such as findAll, findById and deleteById are not user-scoped.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByUserId(Long userId, Pageable pageable);

    @Query("""
            SELECT new com.example.expensetracker.transaction.TransactionExportDTO(
                transaction.occurredOn, category.name, transaction.amount, transaction.note)
            FROM Transaction transaction
            JOIN Category category ON category.id = transaction.categoryId
                AND category.userId = transaction.userId
            WHERE transaction.userId = :userId
                AND category.userId = :userId
            ORDER BY transaction.occurredOn DESC, transaction.id DESC
            """)
    Slice<TransactionExportDTO> findExportRowsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT transaction FROM Transaction transaction
            WHERE transaction.userId = :userId
                AND (:#{#filters.keyword} IS NULL OR LOCATE(:#{#filters.keyword}, transaction.note) > 0)
                AND (:#{#filters.categoryId} IS NULL OR transaction.categoryId = :#{#filters.categoryId})
                AND (:#{#filters.startDate} IS NULL OR transaction.occurredOn >= :#{#filters.startDate})
                AND (:#{#filters.endDate} IS NULL OR transaction.occurredOn <= :#{#filters.endDate})
            """)
    Page<Transaction> searchByUserId(@Param("userId") Long userId,
            @Param("filters") TransactionFilterDTO filters, Pageable pageable);

    List<Transaction> findAllByUserId(Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT transaction FROM Transaction transaction
            WHERE transaction.id = :id AND transaction.userId = :userId
            """)
    Optional<Transaction> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    long countByCategoryIdAndUserId(Long categoryId, Long userId);

    @Query("""
            SELECT COALESCE(SUM(transaction.amount), 0)
            FROM Transaction transaction
            JOIN Category category ON category.id = transaction.categoryId
                AND category.userId = transaction.userId
            WHERE transaction.userId = :userId
                AND category.userId = :userId
                AND category.id = :categoryId
                AND category.type = com.example.expensetracker.category.CategoryType.CHI
                AND transaction.occurredOn >= :startDate
                AND transaction.occurredOn <= :endDate
            """)
    BigDecimal sumExpensesByUserIdAndCategoryIdAndDateRange(@Param("userId") Long userId,
            @Param("categoryId") Long categoryId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(value = """
            SELECT new com.example.expensetracker.home.CategoryExpenseDTO(category.name, SUM(transaction.amount))
            FROM Transaction transaction
            JOIN Category category ON category.id = transaction.categoryId
                AND category.userId = transaction.userId
            WHERE transaction.userId = :userId
                AND category.userId = :userId
                AND category.type = com.example.expensetracker.category.CategoryType.CHI
                AND transaction.occurredOn >= :startDate
                AND transaction.occurredOn <= :endDate
            GROUP BY category.id, category.name
            ORDER BY category.id ASC
            """, countQuery = """
            SELECT COUNT(DISTINCT category.id)
            FROM Transaction transaction
            JOIN Category category ON category.id = transaction.categoryId
                AND category.userId = transaction.userId
            WHERE transaction.userId = :userId
                AND category.userId = :userId
                AND category.type = com.example.expensetracker.category.CategoryType.CHI
                AND transaction.occurredOn >= :startDate
                AND transaction.occurredOn <= :endDate
            """)
    Page<CategoryExpenseDTO> sumExpensesByCategoryForUserAndDateRange(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(transaction.amount), 0)
            FROM Transaction transaction
            JOIN Category category ON category.id = transaction.categoryId
                AND category.userId = transaction.userId
            WHERE transaction.userId = :userId
                AND category.userId = :userId
                AND category.type = com.example.expensetracker.category.CategoryType.THU
                AND transaction.occurredOn >= :startDate
                AND transaction.occurredOn <= :endDate
            """)
    BigDecimal sumIncomeByUserIdAndDateRange(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(transaction.amount), 0)
            FROM Transaction transaction
            JOIN Category category ON category.id = transaction.categoryId
                AND category.userId = transaction.userId
            WHERE transaction.userId = :userId
                AND category.userId = :userId
                AND category.type = com.example.expensetracker.category.CategoryType.CHI
                AND transaction.occurredOn >= :startDate
                AND transaction.occurredOn <= :endDate
            """)
    BigDecimal sumExpenseByUserIdAndDateRange(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            DELETE FROM Transaction entity
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Transaction entity
            SET entity.categoryId = :categoryId,
                entity.amount = :amount,
                entity.occurredOn = :occurredOn,
                entity.note = :note
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int updateByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("amount") BigDecimal amount,
            @Param("occurredOn") LocalDate occurredOn,
            @Param("note") String note);
}
