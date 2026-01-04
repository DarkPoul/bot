package com.shiftbot.service;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void updatesExistingUserDetails() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        User existing = new User(1L, "old_username", "Old Name", "", Role.SELLER, UserStatus.PENDING, null, null);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(existing));

        AuthService authService = new AuthService(usersRepository, ZoneId.of("Europe/Kyiv"));
        User result = authService.onboard(1L, "new_username", "New Name");

        assertEquals(existing, result);
        verify(usersRepository).update(argThat(u -> "new_username".equals(u.getUsername()) && "New Name".equals(u.getFullName())));
        verify(usersRepository, never()).save(any());
    }
}
