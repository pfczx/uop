package com.platform.uop.users.bootstrap;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminBootstrapProperties properties;

  @Override
  public void run(String... args) {

    if (!properties.enabled()) {
      return;
    }

    if (userRepository.existsByRole(UserRole.ADMIN)) {
      return;
    }

    User user = User.builder()
        .id(UUID.randomUUID())
        .email(properties.email())
        .passwordHash(
            passwordEncoder.encode(properties.password()))
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    userRepository.save(user);
  }
}
