package com.example.expensetracker.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Use methods with userId for user-owned data, including lookups before updates.
 * The caller must supply the authenticated user's ID.
 * Inherited methods such as findAll, findById and deleteById are not user-scoped.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserId(Long userId);

    Page<Category> findByUserId(Long userId, Pageable pageable);

    Page<Category> findByUserIdAndIdIn(Long userId, Set<Long> categoryIds, Pageable pageable);

    List<Category> findAllByUserId(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT category FROM Category category
            WHERE category.id = :id
                AND category.userId = :userId
            """)
    Optional<Category> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            DELETE FROM Category entity
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Category entity
            SET entity.name = :name,
                entity.type = :type
            WHERE entity.id = :id
                AND entity.userId = :userId
            """)
    int updateByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("type") CategoryType type);
}
