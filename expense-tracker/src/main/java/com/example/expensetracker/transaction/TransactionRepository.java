package com.example.expensetracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    List<Transaction> findAllByUserId(Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

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
