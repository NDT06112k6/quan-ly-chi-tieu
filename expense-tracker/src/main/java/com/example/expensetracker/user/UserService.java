package com.example.expensetracker.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    private static final String DEFAULT_ROLE = "USER";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        userRepository.saveAndFlush(user);
    }

    private void validatePassword(UserRegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw UserRegistrationException.passwordsDoNotMatch();
        }

        int passwordByteLength = dto.getPassword().getBytes(StandardCharsets.UTF_8).length;
        if (passwordByteLength > BCRYPT_MAX_PASSWORD_BYTES) {
            throw UserRegistrationException.passwordExceedsBcryptLimit();
        }
    }
}
