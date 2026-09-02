package com.platform.uop.setup.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.setup.exception.SetupAlreadyCompletedException;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SetupService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public SetupResponse createAdmin(CreateAdminRequest request) {

    if (userRepository.existsByRole(UserRole.ADMIN)) {
      throw new SetupAlreadyCompletedException();
    }

    User user = User.builder()
        .id(UUID.randomUUID())
        .email(request.email())
        .passwordHash(passwordEncoder.encode(request.password()))
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);

    return new SetupResponse(
        saved.getId(),
        saved.getEmail(),
        saved.getRole(),
        saved.getStatus());
  }
}
