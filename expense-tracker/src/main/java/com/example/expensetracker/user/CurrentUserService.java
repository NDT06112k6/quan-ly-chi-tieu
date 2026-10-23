package com.example.expensetracker.user;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CurrentUserService {

    private static final String ACCESS_DENIED_MESSAGE = "Authenticated user is not available";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
        if (!user.isEnabled()) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        Long userId = user.getId();
        return userId;
    }

    public Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = getUserId(authentication);
        return userId;
    }
}
