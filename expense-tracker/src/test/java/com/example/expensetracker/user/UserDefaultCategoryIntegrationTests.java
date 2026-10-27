package com.example.expensetracker.user;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Transactional
class UserDefaultCategoryIntegrationTests {

    private static final int DEFAULT_CATEGORY_COUNT = 6;
    private static final int PAGE_SIZE = 20;

    private final UserService userService;
    private final UserRepository userRepository;

    @MockitoSpyBean
    private CategoryRepository categoryRepository;

    @Autowired
    UserDefaultCategoryIntegrationTests(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Test
    void persistsDefaultNamesAndTypesForNewlyRegisteredUser() {
        UserRegisterDTO dto = registrationDto();

        userService.registerUser(dto);

        User registered = userRepository.findByEmail(dto.getEmail()).orElseThrow();
        List<Category> categories = categoriesFor(registered.getId());
        assertThat(categories).hasSize(DEFAULT_CATEGORY_COUNT);
        assertThat(categories).allSatisfy(category -> {
            assertThat(category.getUserId()).isEqualTo(registered.getId());
            assertThat(category.getId()).isNotNull();
        });
        assertThat(categories)
                .extracting(Category::getName, Category::getType)
                .containsExactlyInAnyOrder(
                        tuple("Ăn uống", CategoryType.CHI),
                        tuple("Đi lại", CategoryType.CHI),
                        tuple("Mua sắm", CategoryType.CHI),
                        tuple("Hóa đơn", CategoryType.CHI),
                        tuple("Lương", CategoryType.THU),
                        tuple("Thu nhập khác", CategoryType.THU));
    }

    @Test
    void differentUsersReceiveSeparateOwnedCategoryRows() {
        UserRegisterDTO first = registrationDto();
        UserRegisterDTO second = registrationDto();
        userService.registerUser(first);
        userService.registerUser(second);
        User firstUser = userRepository.findByEmail(first.getEmail()).orElseThrow();
        User secondUser = userRepository.findByEmail(second.getEmail()).orElseThrow();

        List<Category> firstCategories = categoriesFor(firstUser.getId());
        List<Category> secondCategories = categoriesFor(secondUser.getId());

        assertThat(firstCategories).hasSize(DEFAULT_CATEGORY_COUNT);
        assertThat(secondCategories).hasSize(DEFAULT_CATEGORY_COUNT);
        for (Category category : firstCategories) {
            assertThat(categoryRepository.findByIdAndUserId(category.getId(), secondUser.getId())).isEmpty();
        }
        for (Category category : secondCategories) {
            assertThat(categoryRepository.findByIdAndUserId(category.getId(), firstUser.getId())).isEmpty();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackUserAndAlreadyInsertedCategoriesWhenLaterCategoryCreationFails() {
        UserRegisterDTO dto = registrationDto();
        AtomicReference<Long> generatedUserId = new AtomicReference<>();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Simulated category failure");
        doAnswer(invocation -> {
            List<Category> categories = invocation.getArgument(0);
            Category first = categories.getFirst();
            generatedUserId.set(first.getUserId());
            categoryRepository.saveAndFlush(first);
            categoryRepository.saveAndFlush(categories.get(1));
            assertThat(userRepository.findByEmail(dto.getEmail())).isPresent();
            assertThat(categoriesFor(first.getUserId())).hasSize(2);
            throw failure;
        }).when(categoryRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> userService.registerUser(dto)).isSameAs(failure);

        assertThat(generatedUserId.get()).isNotNull();
        assertThat(userRepository.findByEmail(dto.getEmail())).isEmpty();
        assertThat(categoriesFor(generatedUserId.get())).isEmpty();
    }

    private List<Category> categoriesFor(Long userId) {
        List<Category> categories = categoryRepository.findByUserId(userId, PageRequest.of(0, PAGE_SIZE)).getContent();
        return categories;
    }

    private UserRegisterDTO registrationDto() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setEmail("default-category-" + UUID.randomUUID() + "@example.invalid");
        dto.setDisplayName("Default category test");
        dto.setPassword("registration-test-password");
        dto.setConfirmPassword("registration-test-password");
        return dto;
    }
}
