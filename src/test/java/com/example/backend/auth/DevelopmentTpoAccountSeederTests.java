package com.example.backend.auth;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.service.AuthService;
import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.config.DevelopmentTpoAccountSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:devtpotest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jwt.secret=test-only-secret-for-development-profile-tests-1234567890",
        "placementos.dev.tpo.password=Placement@123"
})
@ActiveProfiles("dev")
@Transactional
class DevelopmentTpoAccountSeederTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DevelopmentTpoAccountSeeder accountSeeder;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private AuthService authService;

    @Test
    void seedsOnlyOneBcryptTpoAccountThatCanAuthenticate() throws Exception {
        User seededUser = userRepository.findByEmail("tpo@placementos.com").orElseThrow();
        long initialUserCount = userRepository.count();

        accountSeeder.run(mock(ApplicationArguments.class));

        assertEquals(initialUserCount, userRepository.count());
        assertEquals("Placement Officer", seededUser.getName());
        assertEquals(Role.TPO_ADMIN, seededUser.getRole());
        assertNotEquals("Placement@123", seededUser.getPassword());
        assertTrue(passwordEncoder.matches("Placement@123", seededUser.getPassword()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(seededUser.getEmail());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TPO_ADMIN")));

        LoginRequest request = new LoginRequest();
        request.setEmail("tpo@placementos.com");
        request.setPassword("Placement@123");
        AuthResponse response = authService.login(request);

        assertEquals(Role.TPO_ADMIN, response.getRole());
        assertNotNull(response.getToken());
    }
}
