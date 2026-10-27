package com.example.expensetracker.user;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTests {

    private static final Long USER_ID = 42L;
    private static final String EMAIL = "default-categories@example.invalid";
    private static final String PASSWORD = "registration-test-password";
    private static final String PASSWORD_HASH = "test-encoded-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CategoryRepository categoryRepository;

    @Captor
    private ArgumentCaptor<List<Category>> categoriesCaptor;

    @InjectMocks
    private UserService userService;

    @Test
    void createsSixDefaultsWithGeneratedOwnerIdAfterSavingNewUser() {
        configureSavedUser();

        userService.registerUser(registrationDto());

        InOrder registrationWrites = inOrder(userRepository, categoryRepository);
        registrationWrites.verify(userRepository).saveAndFlush(any(User.class));
        registrationWrites.verify(categoryRepository).saveAllAndFlush(categoriesCaptor.capture());
        assertThat(categoriesCaptor.getValue())
                .extracting(Category::getName, Category::getType)
                .containsExactly(
                        tuple("Ăn uống", CategoryType.CHI),
                        tuple("Đi lại", CategoryType.CHI),
                        tuple("Mua sắm", CategoryType.CHI),
                        tuple("Hóa đơn", CategoryType.CHI),
                        tuple("Lương", CategoryType.THU),
                        tuple("Thu nhập khác", CategoryType.THU));
        assertThat(categoriesCaptor.getValue()).allSatisfy(category -> {
            assertThat(category.getUserId()).isEqualTo(USER_ID);
            assertThat(category.getId()).isNull();
        });
    }

    @Test
    void duplicateEmailDoesNotCreateCategoriesOrEncodePassword() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(registrationDto()))
                .isInstanceOf(UserRegistrationException.class);

        verifyNoInteractions(categoryRepository, passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void mismatchedPasswordsDoNotCreateUserOrCategories() {
        UserRegisterDTO dto = registrationDto();
        dto.setConfirmPassword("different-test-password");

        assertThatThrownBy(() -> userService.registerUser(dto)).isInstanceOf(UserRegistrationException.class);

        verifyNoInteractions(categoryRepository, passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void userPersistenceFailureStopsBeforeCategoryCreation() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("User insert failed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> userService.registerUser(registrationDto())).isSameAs(failure);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void categoryPersistenceFailurePropagatesInsteadOfReportingSuccess() {
        configureSavedUser();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Category insert failed");
        when(categoryRepository.saveAllAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> userService.registerUser(registrationDto())).isSameAs(failure);
    }

    private void configureSavedUser() {
        User saved = new User(EMAIL, PASSWORD_HASH, "User");
        ReflectionTestUtils.setField(saved, "id", USER_ID);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(saved);
    }

    private UserRegisterDTO registrationDto() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setEmail(EMAIL);
        dto.setDisplayName("User");
        dto.setPassword(PASSWORD);
        dto.setConfirmPassword(PASSWORD);
        return dto;
    }
}
