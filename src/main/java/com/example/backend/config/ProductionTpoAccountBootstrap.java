package com.example.backend.config;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionTpoAccountBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProductionTpoAccountBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public ProductionTpoAccountBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${placementos.bootstrap.tpo.enabled:false}") boolean enabled,
            @Value("${placementos.bootstrap.tpo.email:}") String email,
            @Value("${placementos.bootstrap.tpo.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Production TPO bootstrap is enabled, but PLACEMENTOS_BOOTSTRAP_TPO_EMAIL "
                            + "and PLACEMENTOS_BOOTSTRAP_TPO_PASSWORD must both be non-blank");
        }

        String normalizedEmail = email.trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }

        User user = new User();
        user.setName("Placement Officer");
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.TPO_ADMIN);
        userRepository.save(user);
        logger.info("Bootstrapped production TPO_ADMIN account for {}", normalizedEmail);
    }
}
