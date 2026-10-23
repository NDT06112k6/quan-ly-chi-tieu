package com.example.expensetracker.user;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRegistrationExceptionHandlerTests {

    private final UserRegistrationExceptionHandler exceptionHandler = new UserRegistrationExceptionHandler();

    @ParameterizedTest
    @ValueSource(strings = {"uk_users_email", "users.uk_users_email"})
    void handlesConcurrentDuplicateEmailWithoutPreservingPasswords(String constraintName) {
        ConstraintViolationException databaseException = new ConstraintViolationException(
                "Duplicate key", new SQLException(), constraintName);
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "Insert failed", databaseException);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("email", "registration@example.invalid");
        request.setParameter("displayName", "Registration Test");
        request.setParameter("password", "private-test-password");
        request.setParameter("confirmPassword", "private-test-password");

        ModelAndView result = exceptionHandler.handleDataIntegrityException(exception, request);

        assertThat(result.getViewName()).isEqualTo("user/register");
        UserRegisterDTO dto = (UserRegisterDTO) result.getModel().get(UserRegisterDTO.MODEL_ATTRIBUTE);
        assertThat(dto).isNotNull();
        assertThat(dto.getEmail()).isEqualTo("registration@example.invalid");
        assertThat(dto.getPassword()).isNull();
        assertThat(dto.getConfirmPassword()).isNull();
        BindingResult bindingResult = (BindingResult) result.getModel()
                .get(BindingResult.MODEL_KEY_PREFIX + UserRegisterDTO.MODEL_ATTRIBUTE);
        assertThat(bindingResult).isNotNull();
        assertThat(bindingResult.hasFieldErrors("email")).isTrue();
    }

    @Test
    void preservesUnrelatedDatabaseConstraintFailures() {
        ConstraintViolationException databaseException = new ConstraintViolationException(
                "Constraint failed", new SQLException(), "another_constraint");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "Insert failed", databaseException);

        assertThatThrownBy(() -> exceptionHandler.handleDataIntegrityException(
                exception, new MockHttpServletRequest()))
                .isSameAs(exception);
    }

    @Test
    void preservesDatabaseFailuresWithoutConstraintMetadata() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Insert failed");

        assertThatThrownBy(() -> exceptionHandler.handleDataIntegrityException(
                exception, new MockHttpServletRequest()))
                .isSameAs(exception);
    }
}
