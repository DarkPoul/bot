package com.shiftbot.service;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UsersRepository usersRepository;
    private AuditService auditService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        auditService = mock(AuditService.class);
        authService = new AuthService(usersRepository, auditService, ZoneId.of("UTC"));
    }

    @Test
    void onboard_returnsExistingUserWithoutAudit() {
        User existing = new User(1L, "u", "User", "", Role.SELLER, UserStatus.ACTIVE, null, null);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(existing));

        User result = authService.onboard(1L, "u", "User");

        assertSame(existing, result);
        verifyNoInteractions(auditService);
        verify(usersRepository, never()).save(any());
    }

    @Test
    void onboard_createsNewUserAndLogsAudit() {
        when(usersRepository.findById(2L)).thenReturn(Optional.empty());

        User result = authService.onboard(2L, "newuser", "New User");

        assertEquals(2L, result.getUserId());
        assertEquals("newuser", result.getUsername());
        verify(usersRepository).save(any(User.class));
        verify(auditService).logEvent(eq(2L), eq("user_onboard"), eq("user"), eq("2"), any());
    }
}
