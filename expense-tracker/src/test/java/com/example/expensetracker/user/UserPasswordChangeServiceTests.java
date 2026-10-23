package com.example.expensetracker.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPasswordChangeServiceTests {

    private static final String EMAIL = "password-change@example.invalid";
    private static final String OLD_PASSWORD = "old-test-password";
    private static final String NEW_PASSWORD = "new-test-password";
    private static final String OLD_HASH = "old-test-hash";
    private static final String NEW_HASH = "new-test-hash";
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
        User user = new User(EMAIL, OLD_HASH, "User");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void doesNotEncodeOrUpdateWhenOldPasswordIsWrong() {
        when(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).thenReturn(false);
        assertThatThrownBy(() -> userService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(UserPasswordChangeException.class);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).updatePasswordIfUnchanged(anyString(), anyString(), anyString());
    }

    @Test
    void encodesNewPasswordAndUpdatesOnlyTheMatchingAccountAndPreviousHash() {
        when(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
        when(userRepository.updatePasswordIfUnchanged(EMAIL, OLD_HASH, NEW_HASH)).thenReturn(1);
        userService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD);
        verify(userRepository).updatePasswordIfUnchanged(EMAIL, OLD_HASH, NEW_HASH);
    }

    @Test
    void rejectsConcurrentChangeRatherThanReportingSuccess() {
        when(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
        when(userRepository.updatePasswordIfUnchanged(EMAIL, OLD_HASH, NEW_HASH)).thenReturn(0);
        assertThatThrownBy(() -> userService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(UserPasswordChangeException.class);
    }

    @Test
    void doesNotPassOverlongOldPasswordToEncoder() {
        String overlongPassword = "界".repeat(25);
        assertThatThrownBy(() -> userService.changePassword(EMAIL, overlongPassword, NEW_PASSWORD))
                .isInstanceOf(UserPasswordChangeException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
