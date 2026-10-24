package com.example.expensetracker.common.web;

import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LayoutModelAdviceTests {

    private static final String USERNAME = "layout-test@example.invalid";

    @Mock
    private UserService userService;

    @InjectMocks
    private LayoutModelAdvice layoutModelAdvice;

    @Test
    void skipsProfileLookupWhenAuthenticationIsMissing() {
        UserProfileDTO profile = layoutModelAdvice.loadLayoutProfile(null);

        assertNull(profile);
        verifyNoInteractions(userService);
    }

    @Test
    void skipsAnonymousAndUnauthenticatedPrincipals() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, null);

        assertNull(layoutModelAdvice.loadLayoutProfile(anonymous));
        assertNull(layoutModelAdvice.loadLayoutProfile(unauthenticated));
        verifyNoInteractions(userService);
    }

    @Test
    void loadsOnlyTheAuthenticatedUsersProfile() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(USERNAME, null, List.of());
        UserProfileDTO expectedProfile = new UserProfileDTO(USERNAME, "Minh");
        when(userService.getProfile(USERNAME)).thenReturn(expectedProfile);

        UserProfileDTO profile = layoutModelAdvice.loadLayoutProfile(authentication);

        assertSame(expectedProfile, profile);
        verify(userService).getProfile(USERNAME);
    }

    @Test
    void preservesProfileLookupFailure() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(USERNAME, null, List.of());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test database failure");
        when(userService.getProfile(USERNAME)).thenThrow(failure);

        assertThatThrownBy(() -> layoutModelAdvice.loadLayoutProfile(authentication)).isSameAs(failure);
    }
}
