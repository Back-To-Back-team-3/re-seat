package com.backtoback.reseat.domain.user.admin.seeder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile(
    {
        "local",
        "default"
    }
)
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_PASSWORD:admin1234!}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@reseat.com";

        if (!userRepository.existsByEmail(adminEmail)) {
            User admin
                = User
                    .builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .name("Re-Seat 관리자")
                    .phone("010-0000-0000")
                    .role(UserRole.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .isVerified(true)
                    .build();

            userRepository.save(admin);
            log.info("Successfully seeded default admin user: {}", adminEmail);
        }
    }
}
