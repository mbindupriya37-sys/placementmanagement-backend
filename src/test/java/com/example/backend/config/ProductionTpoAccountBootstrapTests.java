package com.example.backend.config;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionTpoAccountBootstrapTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ApplicationArguments applicationArguments = mock(ApplicationArguments.class);

    @Test
    void disabledBootstrapDoesNothing() {
        ProductionTpoAccountBootstrap bootstrap = bootstrap(false, "admin@example.com", "secret");

        bootstrap.run(applicationArguments);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void enabledBootstrapFailsWithoutCredentials() {
        ProductionTpoAccountBootstrap bootstrap = bootstrap(true, " ", "secret");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> bootstrap.run(applicationArguments));

        assertTrue(exception.getMessage().contains("must both be non-blank"));
        assertTrue(!exception.getMessage().contains("secret"));
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void enabledBootstrapCreatesBcryptTpoAdminWhenEmailIsAbsent() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        ProductionTpoAccountBootstrap bootstrap = bootstrap(true, " admin@example.com ", "secret");

        bootstrap.run(applicationArguments);

        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        User user = savedUser.getValue();
        assertEquals("admin@example.com", user.getEmail());
        assertEquals("Placement Officer", user.getName());
        assertEquals(Role.TPO_ADMIN, user.getRole());
        assertTrue(passwordEncoder.matches("secret", user.getPassword()));
        assertTrue(!user.getPassword().equals("secret"));
    }

    @Test
    void enabledBootstrapLeavesExistingUserUnchanged() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
        ProductionTpoAccountBootstrap bootstrap = bootstrap(true, "admin@example.com", "secret");

        bootstrap.run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    private ProductionTpoAccountBootstrap bootstrap(boolean enabled, String email, String password) {
        return new ProductionTpoAccountBootstrap(userRepository, passwordEncoder, enabled, email, password);
    }
}
