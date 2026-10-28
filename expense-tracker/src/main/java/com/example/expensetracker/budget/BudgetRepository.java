package com.example.expensetracker.budget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Use methods with userId for user-owned data, including lookups before updates.
 * The caller must supply the authenticated user's ID.
 * Inherited methods such as findAll, findById and deleteById are not user-scoped.
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Page<Budget> findByUserId(Long userId, Pageable pageable);

    Optional<Budget> findByUserIdAndCategoryIdAndPeriodAndType(
            Long userId, Long categoryId, String period, BudgetType type);

    List<Budget> findAllByUserId(Long userId);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryIdAndUserId(Long categoryId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Budget entity
            SET entity.limitAmount = :limitAmount
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int updateLimitAmountByIdAndUserId(
            @Param("id") Long id, @Param("userId") Long userId, @Param("limitAmount") BigDecimal limitAmount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            DELETE FROM Budget entity
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Budget entity
            SET entity.categoryId = :#{#budget.categoryId},
                entity.period = :#{#budget.period},
                entity.type = :#{#budget.type},
                entity.limitAmount = :#{#budget.limitAmount}
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int updateByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("budget") BudgetDTO budget);
}
