package com.example.backend.auth;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.service.AuthService;
import com.example.backend.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersStudentWithHashedPasswordAndJwt() {
        assertRegistrationSucceeds("student-auth@example.com", Role.STUDENT);
    }

    @Test
    void registersCompanyRecruiterWithCompanyRecruiterRole() {
        assertRegistrationSucceeds("recruiter-auth@example.com", Role.COMPANY_RECRUITER);
    }

    @Test
    void rejectsStaffRolesFromPublicRegistration() {
        assertThrows(BadRequestException.class,
                () -> authService.register(registration("admin-auth@example.com", Role.TPO_ADMIN)));
        assertThrows(BadRequestException.class,
                () -> authService.register(registration("coordinator-auth@example.com", Role.DEPARTMENT_COORDINATOR)));
    }

    @Test
    void rejectsDuplicateEmail() {
        User user = new User();
        user.setName("Existing User");
        user.setEmail("duplicate-auth@example.com");
        user.setPassword("existing-password");
        user.setRole(Role.STUDENT);
        userRepository.save(user);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> authService.register(registration("duplicate-auth@example.com", Role.COMPANY_RECRUITER)));

        assertEquals("Email is already registered", exception.getMessage());
    }

    private void assertRegistrationSucceeds(String email, Role role) {
        AuthResponse response = authService.register(registration(email, role));
        User savedUser = userRepository.findByEmail(email).orElseThrow();

        assertEquals(role, savedUser.getRole());
        assertNotEquals("password123", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("password123", savedUser.getPassword()));
        assertEquals(role, response.getRole());
        assertNotNull(response.getToken());
    }

    private RegisterRequest registration(String email, Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail(email);
        request.setPassword("password123");
        request.setRole(role);
        return request;
    }
}
