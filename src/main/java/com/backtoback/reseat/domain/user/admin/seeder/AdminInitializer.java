package com.backtoback.reseat.domain.user.admin.seeder;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "default"})
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@test.com";

        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode("password123!"))
                    .name("관리자")
                    .phone("010-1234-5678")
                    .role(UserRole.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .isVerified(true)
                    .build();

            userRepository.save(admin);
            log.info("Successfully seeded default admin user: {}", adminEmail);
        } else {
            userRepository.findByEmail(adminEmail).ifPresent(user -> {
                if (user.getRole() != UserRole.ADMIN) {
                    user.updateRole(UserRole.ADMIN);
                    userRepository.save(user);
                    log.info("Updated existing user {} to role ADMIN", adminEmail);
                }
            });
        }
    }
}
