package com.shiftbot.repository;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersRepositoryTest {

    @Mock
    private SheetsClient sheetsClient;

    @Test
    void updatesExistingRowAndInvalidatesCache() {
        List<List<Object>> rows = Collections.singletonList(List.of(
                "1", "user1", "User One", "050", "TM", "PENDING", "", ""
        ));
        when(sheetsClient.readRange("users!A2:H")).thenReturn(rows);
        UsersRepository repository = new UsersRepository(sheetsClient, Duration.ofMinutes(5));

        repository.findAll(); // warm cache
        User updated = new User(1L, "user1", "User One", "050", Role.TM, UserStatus.ACTIVE, null, null);

        repository.updateRow(1L, updated);

        ArgumentCaptor<List<List<Object>>> valuesCaptor = ArgumentCaptor.forClass(List.class);
        verify(sheetsClient).updateRange(eq("users!A2:H2"), valuesCaptor.capture());
        List<Object> updatedRow = valuesCaptor.getValue().get(0);
        assertEquals("1", updatedRow.get(0));
        assertEquals("user1", updatedRow.get(1));
        assertEquals("User One", updatedRow.get(2));
        assertEquals("050", updatedRow.get(3));
        assertEquals("TM", updatedRow.get(4));
        assertEquals("ACTIVE", updatedRow.get(5));
        verify(sheetsClient, times(2)).readRange("users!A2:H");
    }

    @Test
    void throwsWhenUserNotFound() {
        when(sheetsClient.readRange("users!A2:H")).thenReturn(Collections.emptyList());
        UsersRepository repository = new UsersRepository(sheetsClient, Duration.ofMinutes(5));

        assertThrows(IllegalArgumentException.class, () -> repository.updateRow(99L, new User()));
    }
}
