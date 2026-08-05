package com.platform.uop.users.bootstrap;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  private final String email = "admin@admin.com";
  private final String password = "admin";

  @Override
  public void run(String... args) throws Exception {
    if (!userRepository.existsByEmail("admin@admin.com")) {
      User user = User.builder()
          .id(UUID.randomUUID())
          .email(email)
          .passwordHash(passwordEncoder.encode(password))
          .role(UserRole.ADMIN)
          .status(UserStatus.ACTIVE)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();
      userRepository.save(user);
    }
  }
}
