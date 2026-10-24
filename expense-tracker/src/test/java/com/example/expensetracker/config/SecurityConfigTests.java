package com.example.expensetracker.config;

import com.example.expensetracker.user.CustomUserDetailsService;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTests.TestController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class, SecurityConfigTests.TestController.class})
@MockitoBean(types = {UserRepository.class, UserService.class})
class SecurityConfigTests {

    private static final String EMAIL = "security-test@example.invalid";
    private static final String PASSWORD = "security-test-password";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    SecurityConfigTests(MockMvc mockMvc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/register", "/css/nested/test.css", "/js/nested/test.js"})
    void permitsPublicRequests(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/protected", "/unlisted"})
    void requiresAuthenticationForOtherRequests(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void permitsAuthenticatedRequests() throws Exception {
        mockMvc.perform(get("/protected").with(user(EMAIL)))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatesByEmailWithBcryptPassword() throws Exception {
        stubUser(true);

        mockMvc.perform(formLogin("/login").user(EMAIL).password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(EMAIL).withRoles("USER"));
    }

    @Test
    void rejectsIncorrectPassword() throws Exception {
        stubUser(true);

        mockMvc.perform(formLogin("/login").user(EMAIL).password("incorrect-password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void rejectsDisabledAccount() throws Exception {
        stubUser(false);

        mockMvc.perform(formLogin("/login").user(EMAIL).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void rejectsUnknownEmail() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(formLogin("/login").user(EMAIL).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void requiresCsrfTokenForLoginAndAuthenticatedWrites() throws Exception {
        mockMvc.perform(post("/login").param("username", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/protected").with(user(EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").with(user(EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/protected").with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void logsOutInvalidatesSessionAndRedirectsToLogin() throws Exception {
        stubUser(true);
        MvcResult loginResult = mockMvc.perform(formLogin("/login").user(EMAIL).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/protected"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void encodesSaltedBcryptHashesAndRejectsIncorrectPassword() {
        String passwordHash = passwordEncoder.encode(PASSWORD);
        String secondPasswordHash = passwordEncoder.encode(PASSWORD);

        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(passwordHash).isNotEqualTo(PASSWORD);
        assertThat(secondPasswordHash).isNotEqualTo(passwordHash);
        assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();
        assertThat(passwordEncoder.matches("incorrect-password", passwordHash)).isFalse();
    }

    private void stubUser(boolean enabled) {
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = new User(EMAIL, passwordHash, "Security Test");
        user.setEnabled(enabled);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @RestController
    static class TestController {

        @GetMapping({"/login", "/register", "/css/nested/test.css", "/js/nested/test.js",
                "/protected", "/unlisted"})
        String read() {
            return "ok";
        }

        @PostMapping("/protected")
        String write() {
            return "ok";
        }
    }
}
