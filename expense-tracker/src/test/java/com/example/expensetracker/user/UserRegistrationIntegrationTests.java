package com.example.expensetracker.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserRegistrationIntegrationTests {

    private static final String PASSWORD = "register-test-password";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private String email;

    @Autowired
    UserRegistrationIntegrationTests(
            MockMvc mockMvc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void setUp() {
        email = "register-" + UUID.randomUUID() + "@example.invalid";
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/register"})
    void rendersPublicFormsWithCsrfToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name("user" + path))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("type=\"password\"")));
    }

    @Test
    void registersEnabledUserWithBcryptPasswordAndAuthenticatesByEmail() throws Exception {
        UserRegisterDTO dto = validRegistration();

        mockMvc.perform(registrationRequest(dto).param("role", "ADMIN").param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?success"))
                .andExpect(unauthenticated());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("Registration Test");
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();

        MvcResult loginResult = mockMvc.perform(formLogin("/login").user(email).password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(email).withRoles("USER"))
                .andReturn();
        verifyHomePageAndLogout(loginResult);
    }

    @Test
    void displaysValidationErrorsWithoutSavingOrRepopulatingPasswords() throws Exception {
        UserRegisterDTO dto = validRegistration();
        dto.setEmail("invalid-email");
        dto.setPassword("short");
        dto.setConfirmPassword("");
        dto.setDisplayName(" ");

        mockMvc.perform(registrationRequest(dto))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors(
                        UserRegisterDTO.MODEL_ATTRIBUTE, "email", "password", "confirmPassword", "displayName"))
                .andExpect(content().string(containsString("Email không hợp lệ.")))
                .andExpect(content().string(not(containsString("value=\"short\""))));

        assertThat(userRepository.findByEmail(dto.getEmail())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "password", "confirmPassword", "displayName"})
    void rejectsFieldsExceedingMaximumLength(String fieldName) throws Exception {
        UserRegisterDTO dto = validRegistration();
        BeanWrapperImpl properties = new BeanWrapperImpl(dto);
        properties.setPropertyValue(fieldName, "a".repeat(256));

        mockMvc.perform(registrationRequest(dto))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors(UserRegisterDTO.MODEL_ATTRIBUTE, fieldName));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void displaysPasswordMismatchEscapesUserInputAndDoesNotSave() throws Exception {
        UserRegisterDTO dto = validRegistration();
        dto.setConfirmPassword("different-password");
        dto.setDisplayName("<script>alert(1)</script>");

        mockMvc.perform(registrationRequest(dto))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors(UserRegisterDTO.MODEL_ATTRIBUTE, "confirmPassword"))
                .andExpect(content().string(containsString("Mật khẩu nhập lại không khớp.")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString(PASSWORD))));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitivelyWithoutChangingExistingAccount() throws Exception {
        String existingPasswordHash = passwordEncoder.encode("existing-test-password");
        User existingUser = new User(email, existingPasswordHash, "Existing User");
        userRepository.saveAndFlush(existingUser);
        UserRegisterDTO dto = validRegistration();
        dto.setEmail(email.toUpperCase(Locale.ROOT));

        mockMvc.perform(registrationRequest(dto))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors(UserRegisterDTO.MODEL_ATTRIBUTE, "email"))
                .andExpect(content().string(containsString("Email này đã được sử dụng.")))
                .andExpect(content().string(not(containsString(PASSWORD))));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getId()).isEqualTo(existingUser.getId());
        assertThat(user.getDisplayName()).isEqualTo("Existing User");
        assertThat(user.getPasswordHash()).isEqualTo(existingPasswordHash);
    }

    @Test
    void rejectsMultibytePasswordExceedingBcryptByteLimit() throws Exception {
        UserRegisterDTO dto = validRegistration();
        String multibytePassword = "ậ".repeat(25);
        dto.setPassword(multibytePassword);
        dto.setConfirmPassword(multibytePassword);

        mockMvc.perform(registrationRequest(dto))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors(UserRegisterDTO.MODEL_ATTRIBUTE, "password"))
                .andExpect(content().string(containsString("72 byte UTF-8")))
                .andExpect(content().string(not(containsString(multibytePassword))));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void rejectsRegistrationWithoutCsrfToken() throws Exception {
        UserRegisterDTO dto = validRegistration();

        mockMvc.perform(post("/register")
                        .param("email", dto.getEmail())
                        .param("password", dto.getPassword())
                        .param("confirmPassword", dto.getConfirmPassword())
                        .param("displayName", dto.getDisplayName()))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "logout", "error"})
    void rendersLoginFeedback(String feedback) throws Exception {
        mockMvc.perform(get("/login").param(feedback, ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(loginMessage(feedback))));
    }

    @Test
    void rendersInvalidLoginFeedbackAfterAuthenticationFailure() throws Exception {
        mockMvc.perform(formLogin("/login").user(email).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(loginMessage("error"))))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void servesSharedFormStylesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/css/auth.css"))
                .andExpect(status().isOk());
    }

    private UserRegisterDTO validRegistration() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setEmail(email);
        dto.setPassword(PASSWORD);
        dto.setConfirmPassword(PASSWORD);
        dto.setDisplayName("Registration Test");
        return dto;
    }

    private void verifyHomePageAndLogout(MvcResult loginResult) throws Exception {
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Chào mừng đến với Expense Tracker")));
        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/"))
                .andExpect(redirectedUrl("/login"));
    }

    private MockHttpServletRequestBuilder registrationRequest(UserRegisterDTO dto) {
        MockHttpServletRequestBuilder request = post("/register")
                .with(csrf())
                .param("email", dto.getEmail())
                .param("password", dto.getPassword())
                .param("confirmPassword", dto.getConfirmPassword())
                .param("displayName", dto.getDisplayName());
        return request;
    }

    private String loginMessage(String feedback) {
        String message = switch (feedback) {
            case "success" -> "Đăng ký thành công.";
            case "logout" -> "Bạn đã đăng xuất thành công.";
            default -> "Email hoặc mật khẩu không đúng";
        };
        return message;
    }
}
