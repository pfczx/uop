package com.platform.uop.users.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.CreateUserResponse;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public CreateUserResponse create(CreateUserRequest request) {

    User user = User.builder()
        .id(UUID.randomUUID())
        .email(request.email())
        .passwordHash("TODO")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);

    return new CreateUserResponse(
        saved.getId(),
        saved.getEmail(),
        saved.getRole(),
        saved.getStatus(),
        saved.getCreatedAt());
  }
}
