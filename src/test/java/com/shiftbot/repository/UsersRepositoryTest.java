package com.shiftbot.repository;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UsersRepositoryTest {

    @Test
    void updatesRowAndInvalidatesCache() {
        SheetsClient sheetsClient = mock(SheetsClient.class);
        UsersRepository repository = new UsersRepository(sheetsClient, Duration.ofMinutes(5));

        List<List<Object>> rows = List.of(
                List.of("1", "username", "Full Name", "+380000000", Role.SELLER.name(), UserStatus.ACTIVE.name(), Instant.parse("2024-01-01T00:00:00Z").toString(), "2")
        );
        when(sheetsClient.readRange("users!A2:H"))
                .thenReturn(rows)
                .thenReturn(List.of());

        List<User> users = repository.findAll();
        User user = users.get(0);
        user.setFullName("New Name");

        repository.update(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> rowCaptor = ArgumentCaptor.forClass((Class<List<Object>>) (Class<?>) List.class);
        verify(sheetsClient).updateRow(eq("users!A2:H"), eq(0), rowCaptor.capture());
        assertEquals("New Name", rowCaptor.getValue().get(2));

        repository.findAll();
        verify(sheetsClient, times(2)).readRange("users!A2:H");
        verify(sheetsClient, never()).appendRow(eq("users!A2:H"), anyList());
    }
}
