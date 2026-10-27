package com.example.expensetracker.user;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.category.CategoryRepository;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.PasswordUtil;
import com.example.expensetracker.common.util.UserAccessUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Service
@Validated
public class UserService {

    private static final String DEFAULT_ROLE = "USER";
    private static final int EXPECTED_UPDATE_COUNT = 1;
    private static final List<String> DEFAULT_EXPENSE_CATEGORY_NAMES = List.of("Ăn uống", "Đi lại", "Mua sắm", "Hóa đơn");
    private static final List<String> DEFAULT_INCOME_CATEGORY_NAMES = List.of("Lương", "Thu nhập khác");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void registerUser(UserRegisterDTO dto) {
        String email = dto.getEmail().strip();
        if (userRepository.existsByEmail(email)) {
            throw UserRegistrationException.emailAlreadyExists();
        }
        validatePassword(dto);

        String passwordHash = passwordEncoder.encode(dto.getPassword());
        String displayName = dto.getDisplayName().strip();
        User user = new User(email, passwordHash, displayName);
        user.setRole(DEFAULT_ROLE);
        user.setEnabled(true);
        User registeredUser = userRepository.saveAndFlush(user);
        initializeDefaultCategories(registeredUser.getId());
    }

    private void initializeDefaultCategories(Long userId) {
        List<Category> categories = createCategories(userId, CategoryType.CHI, DEFAULT_EXPENSE_CATEGORY_NAMES);
        List<Category> incomeCategories = createCategories(userId, CategoryType.THU, DEFAULT_INCOME_CATEGORY_NAMES);
        categories.addAll(incomeCategories);
        categoryRepository.saveAllAndFlush(categories);
    }

    private List<Category> createCategories(Long userId, CategoryType type, List<String> names) {
        List<Category> categories = new ArrayList<>();
        for (String name : names) {
            Category category = new Category(userId, name, type);
            categories.add(category);
        }
        return categories;
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(@NotBlank @Email @Size(max = 255) String email) {
        User user = UserAccessUtil.findEnabledUserByEmail(userRepository, email);
        UserProfileDTO profile = new UserProfileDTO(user.getEmail(), user.getDisplayName());
        return profile;
    }

    @Transactional
    public void changePassword(@NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = UserRegisterDTO.MAX_PASSWORD_LENGTH) String oldPassword,
            @NotBlank @Size(min = UserRegisterDTO.MIN_PASSWORD_LENGTH,
                    max = UserRegisterDTO.MAX_PASSWORD_LENGTH) String newPassword) {
        User user = UserAccessUtil.findEnabledUserByEmail(userRepository, email);
        if (!PasswordUtil.isWithinBcryptLimit(oldPassword)
                || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw UserPasswordChangeException.incorrectOldPassword();
        }
        if (!PasswordUtil.isWithinBcryptLimit(newPassword)) {
            throw UserPasswordChangeException.passwordExceedsBcryptLimit();
        }
        String newPasswordHash = passwordEncoder.encode(newPassword);
        // Compare the stored hash to prevent concurrent requests from overwriting a newer password.
        int updatedRows = userRepository.updatePasswordIfUnchanged(email, user.getPasswordHash(), newPasswordHash);
        if (updatedRows != EXPECTED_UPDATE_COUNT) {
            throw UserPasswordChangeException.passwordChangedConcurrently();
        }
    }

    private void validatePassword(UserRegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw UserRegistrationException.passwordsDoNotMatch();
        }

        if (!PasswordUtil.isWithinBcryptLimit(dto.getPassword())) {
            throw UserRegistrationException.passwordExceedsBcryptLimit();
        }
    }
}
