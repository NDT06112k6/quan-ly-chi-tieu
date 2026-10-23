package com.example.expensetracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE User user SET user.passwordHash = :newPasswordHash
            WHERE user.email = :email
                AND user.passwordHash = :currentPasswordHash
                AND user.enabled = true
            """)
    int updatePasswordIfUnchanged(@Param("email") String email,
            @Param("currentPasswordHash") String currentPasswordHash,
            @Param("newPasswordHash") String newPasswordHash);
}
