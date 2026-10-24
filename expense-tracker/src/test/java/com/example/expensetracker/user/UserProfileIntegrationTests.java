package com.example.expensetracker.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class UserProfileIntegrationTests {

    private static final String OLD_PASSWORD = "old-profile-test-password";
    private static final String NEW_PASSWORD = "new-profile-test-password";
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private User owner;
    private User otherUser;
    private String originalHash;

    @Autowired
    UserProfileIntegrationTests(MockMvc mockMvc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        originalHash = passwordEncoder.encode(OLD_PASSWORD);
        owner = userRepository.saveAndFlush(new User(
                "profile-owner-" + suffix + "@example.invalid", originalHash, "Profile Owner"));
        otherUser = userRepository.saveAndFlush(new User(
                "profile-other-" + suffix + "@example.invalid", originalHash, "Other Profile"));
    }

    @Test
    void rendersOnlyAuthenticatedProfileDtoAndCsrfProtectedForm() throws Exception {
        MvcResult result = mockMvc.perform(get("/profile").with(user(owner.getEmail()))
                        .param("email", otherUser.getEmail()))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/view"))
                .andExpect(content().string(containsString(owner.getEmail())))
                .andExpect(content().string(containsString("Profile Owner")))
                .andExpect(content().string(not(containsString(otherUser.getEmail()))))
                .andExpect(content().string(not(containsString(originalHash))))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("action=\"/profile/change-password\"")))
                .andReturn();
        UserProfileDTO profile = (UserProfileDTO) result.getModelAndView().getModel().get("profile");
        assertThat(profile.email()).isEqualTo(owner.getEmail());
        assertThat(profile.displayName()).isEqualTo("Profile Owner");
    }

    @Test
    void changesPasswordOnlyForLoggedInUserAndAuthenticatesWithNewPassword() throws Exception {
        mockMvc.perform(passwordRequest(OLD_PASSWORD, NEW_PASSWORD)
                        .param("email", otherUser.getEmail()).param("id", otherUser.getId().toString())
                        .param("role", "ADMIN").param("displayName", "Changed name"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
        User updated = userRepository.findByEmail(owner.getEmail()).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo(NEW_PASSWORD).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, updated.getPasswordHash())).isFalse();
        assertThat(updated.getRole()).isEqualTo("USER");
        assertThat(updated.getDisplayName()).isEqualTo("Profile Owner");
        assertThat(userRepository.findByEmail(otherUser.getEmail()).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
        mockMvc.perform(formLogin("/login").user(owner.getEmail()).password(OLD_PASSWORD))
                .andExpect(unauthenticated());
        mockMvc.perform(formLogin("/login").user(owner.getEmail()).password(NEW_PASSWORD))
                .andExpect(authenticated().withUsername(owner.getEmail()));
    }

    @Test
    void wrongOldPasswordShowsFieldErrorWithoutSavingOrEchoingPasswords() throws Exception {
        MvcResult result = mockMvc.perform(passwordRequest("incorrect-old-test-password", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("profile/view"))
                .andExpect(model().attributeHasFieldErrors(PasswordChangeDTO.MODEL_ATTRIBUTE, "oldPassword"))
                .andExpect(content().string(containsString("Mật khẩu hiện tại không đúng.")))
                .andExpect(content().string(containsString(owner.getEmail())))
                .andReturn();
        assertPasswordFieldsCleared(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("incorrect-old-test-password");
        assertOriginalPasswordUnchanged();
    }

    @ParameterizedTest
    @CsvSource(value = {"oldPassword|", "oldPassword| ", "newPassword|", "newPassword|short", "newPassword|        "},
            delimiter = '|', emptyValue = "", ignoreLeadingAndTrailingWhitespace = false)
    void rejectsInvalidFormFieldsWithoutEchoingPasswords(String field, String value) throws Exception {
        String oldPassword = OLD_PASSWORD;
        String newPassword = NEW_PASSWORD;
        if (field.equals("oldPassword")) {
            oldPassword = value;
        } else {
            newPassword = value;
        }
        MvcResult result = mockMvc.perform(passwordRequest(oldPassword, newPassword))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("profile/view"))
                .andExpect(model().attributeHasFieldErrors(PasswordChangeDTO.MODEL_ATTRIBUTE, field))
                .andReturn();
        assertPasswordFieldsCleared(result);
        assertOriginalPasswordUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"oldPassword", "newPassword"})
    void rejectsFieldsOverSeventyTwoCharacters(String field) throws Exception {
        String oldPassword = OLD_PASSWORD;
        String newPassword = NEW_PASSWORD;
        if (field.equals("oldPassword")) {
            oldPassword = "x".repeat(73);
        } else {
            newPassword = "x".repeat(73);
        }
        mockMvc.perform(passwordRequest(oldPassword, newPassword))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors(PasswordChangeDTO.MODEL_ATTRIBUTE, field));
        assertOriginalPasswordUnchanged();
    }

    @Test
    void rejectsNewPasswordExceedingBcryptUtf8ByteLimit() throws Exception {
        MvcResult result = mockMvc.perform(passwordRequest(OLD_PASSWORD, "界".repeat(25)))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors(PasswordChangeDTO.MODEL_ATTRIBUTE, "newPassword"))
                .andExpect(content().string(containsString("72 byte UTF-8")))
                .andReturn();
        assertPasswordFieldsCleared(result);
        assertOriginalPasswordUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "界"})
    void acceptsPasswordAtExactlySeventyTwoUtf8Bytes(String character) throws Exception {
        int repetitions = 72 / character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String password = character.repeat(repetitions);
        mockMvc.perform(passwordRequest(OLD_PASSWORD, password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
        String hash = userRepository.findByEmail(owner.getEmail()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Test
    void rejectsRepeatedUseOfOldPasswordAfterSuccessfulChange() throws Exception {
        mockMvc.perform(passwordRequest(OLD_PASSWORD, NEW_PASSWORD))
                .andExpect(redirectedUrl("/profile"));
        mockMvc.perform(passwordRequest(OLD_PASSWORD, "another-new-test-password"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors(PasswordChangeDTO.MODEL_ATTRIBUTE, "oldPassword"));
        String hash = userRepository.findByEmail(owner.getEmail()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/profile/change-password").with(csrf()))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/profile/change-password").with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
        assertOriginalPasswordUnchanged();
    }

    @Test
    void deniesDisabledOrMissingAccounts() throws Exception {
        owner.setEnabled(false);
        userRepository.saveAndFlush(owner);
        mockMvc.perform(get("/profile").with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(passwordRequest(OLD_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/profile").with(user("missing-profile@example.invalid")))
                .andExpect(status().isForbidden());
        assertOriginalPasswordUnchanged();
    }

    @Test
    void escapesDisplayNameAndShowsSuccessMessage() throws Exception {
        owner.setDisplayName("<script>alert(1)</script>");
        userRepository.saveAndFlush(owner);
        mockMvc.perform(get("/profile").with(user(owner.getEmail())).param("success", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString("Đổi mật khẩu thành công.")));
    }

    private MockHttpServletRequestBuilder passwordRequest(String oldPassword, String newPassword) {
        MockHttpServletRequestBuilder request = post("/profile/change-password")
                .with(user(owner.getEmail()))
                .with(csrf())
                .param("oldPassword", oldPassword)
                .param("newPassword", newPassword);
        return request;
    }

    private void assertOriginalPasswordUnchanged() {
        String storedHash = userRepository.findByEmail(owner.getEmail()).orElseThrow().getPasswordHash();
        assertThat(storedHash).isEqualTo(originalHash);
    }

    private void assertPasswordFieldsCleared(MvcResult result) throws Exception {
        PasswordChangeDTO dto = (PasswordChangeDTO) result.getModelAndView().getModel().get(PasswordChangeDTO.MODEL_ATTRIBUTE);
        assertThat(dto.getOldPassword()).isNull();
        assertThat(dto.getNewPassword()).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(OLD_PASSWORD, NEW_PASSWORD, originalHash);
    }
}
