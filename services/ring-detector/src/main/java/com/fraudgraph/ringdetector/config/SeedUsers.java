package com.fraudgraph.ringdetector.config;

import com.fraudgraph.ringdetector.entity.AppUser;
import com.fraudgraph.ringdetector.enums.Role;
import com.fraudgraph.ringdetector.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one demo login per role — analyst, lead, auditor, admin — if it does
 * not exist yet. Done at startup rather than in a Flyway migration so the
 * password comes from the environment (SEED_USER_PASSWORD) instead of being
 * committed. Existing users are left untouched, so changing the variable later
 * does not reset anyone's password.
 */
@Component
class SeedUsers implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    SeedUsers(AppUserRepository users, PasswordEncoder passwordEncoder,
              @Value("${auth.seed-user-password}") String seedPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedPassword.isBlank()) {
            throw new IllegalStateException("SEED_USER_PASSWORD must not be blank");
        }
        for (Role role : Role.values()) {
            String username = role.name().toLowerCase();
            if (!users.existsByUsername(username)) {
                users.save(new AppUser(username, passwordEncoder.encode(seedPassword), role));
            }
        }
    }
}
