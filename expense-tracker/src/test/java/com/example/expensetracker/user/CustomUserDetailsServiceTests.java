package com.example.expensetracker.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTests {

    private static final String EMAIL = "security-test@example.invalid";
    private static final String PASSWORD_HASH = "stored-password-hash";

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadsEmailPasswordAndRoleFromRepository() {
        User user = new User(EMAIL, PASSWORD_HASH, "Security Test");
        user.setRole("ADMIN");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(userDetails.getUsername()).isEqualTo(EMAIL);
        assertThat(userDetails.getPassword()).isEqualTo(PASSWORD_HASH);
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void preservesDisabledAccountStatus() {
        User user = new User(EMAIL, PASSWORD_HASH, "Security Test");
        user.setEnabled(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    void rejectsUnknownEmailWithoutDisclosingItInException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
    }
}
