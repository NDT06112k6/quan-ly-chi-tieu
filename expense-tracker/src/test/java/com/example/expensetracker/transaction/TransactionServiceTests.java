package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTests {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void preservesExportDatabaseFailureRatherThanReturningHeaderOnlySuccess() {
        Long userId = 42L;
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(transactionRepository.findExportRowsByUserId(eq(userId), any(Pageable.class))).thenThrow(failure);
        TransactionService transactionService = new TransactionService(transactionRepository, categoryRepository);
        assertThatThrownBy(() -> transactionService.exportTransactionsCsv(userId)).isSameAs(failure);
    }

    @Test
    void stopsBeforePersistenceWhenCategoryOwnershipCheckFails() {
        Long userId = 42L;
        Long categoryId = 7L;
        TransactionDTO dto = new TransactionDTO();
        dto.setAmount(new BigDecimal("12.34"));
        dto.setOccurredOn(LocalDate.of(2026, 10, 23));
        dto.setCategoryId(categoryId);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());
        TransactionService transactionService = new TransactionService(transactionRepository, categoryRepository);

        assertThatThrownBy(() -> transactionService.createTransaction(dto, userId))
                .isInstanceOf(InvalidTransactionCategoryException.class);

        verify(categoryRepository).findByIdAndUserId(categoryId, userId);
        verifyNoInteractions(transactionRepository);
    }
}
