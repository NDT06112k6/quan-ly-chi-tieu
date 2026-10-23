package com.example.expensetracker.common.util;

import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;

public final class UserAccessUtil {

    private static final String ACCESS_DENIED_MESSAGE = "Authenticated user is not available";

    private UserAccessUtil() {
    }

    public static User findEnabledUserByEmail(UserRepository userRepository, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
        if (!user.isEnabled()) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }
        return user;
    }
}
