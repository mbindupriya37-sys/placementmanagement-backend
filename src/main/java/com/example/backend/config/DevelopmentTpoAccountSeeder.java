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
@Profile("dev")
public class DevelopmentTpoAccountSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevelopmentTpoAccountSeeder.class);
    private static final String EMAIL = "tpo@placementos.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public DevelopmentTpoAccountSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${placementos.dev.tpo.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(EMAIL)) {
            return;
        }
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "Set PLACEMENTOS_DEV_TPO_PASSWORD to seed the development TPO account");
        }

        User user = new User();
        user.setName("Placement Officer");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.TPO_ADMIN);
        userRepository.save(user);
        logger.info("Seeded development TPO_ADMIN account for {}", EMAIL);
    }
}
