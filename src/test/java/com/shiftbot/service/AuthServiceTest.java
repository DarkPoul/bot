package com.shiftbot.service;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsersRepository usersRepository;

    @Test
    void returnsPendingForExistingPendingUser() {
        User user = new User(1L, "user1", "User One", "", Role.SELLER, UserStatus.PENDING, null, null);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
        AuthService service = new AuthService(usersRepository, ZoneId.of("UTC"));

        AuthService.OnboardResult result = service.onboard(1L, "user1", "User One");

        assertFalse(result.allowed());
        assertEquals(user, result.user());
        assertTrue(result.message().contains("очікує підтвердження"));
        verify(usersRepository, never()).save(any());
    }

    @Test
    void returnsAllowedForActiveUser() {
        User user = new User(2L, "user2", "User Two", "", Role.SELLER, UserStatus.ACTIVE, null, null);
        when(usersRepository.findById(2L)).thenReturn(Optional.of(user));
        AuthService service = new AuthService(usersRepository, ZoneId.of("UTC"));

        AuthService.OnboardResult result = service.onboard(2L, "user2", "User Two");

        assertTrue(result.allowed());
        assertNull(result.message());
        assertEquals(user, result.user());
        verify(usersRepository, never()).save(any());
    }

    @Test
    void createsPendingUserWhenNotFound() {
        when(usersRepository.findById(3L)).thenReturn(Optional.empty());
        AuthService service = new AuthService(usersRepository, ZoneId.of("UTC"));

        AuthService.OnboardResult result = service.onboard(3L, "user3", "User Three");

        assertFalse(result.allowed());
        assertEquals(UserStatus.PENDING, result.user().getStatus());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(usersRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getUserId());
        assertEquals("user3", captor.getValue().getUsername());
        assertEquals("User Three", captor.getValue().getFullName());
    }
}
