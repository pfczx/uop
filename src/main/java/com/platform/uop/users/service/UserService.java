package com.platform.uop.users.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.UpdateEmailRequest;
import com.platform.uop.users.dto.UserResponse;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.exeption.UserAlreadyExistsException;
import com.platform.uop.users.exeption.UserNotFoundException;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public UserResponse create(CreateUserRequest request) {

    if (userRepository.existsByEmail(request.email())) {
      throw new UserAlreadyExistsException(request.email());
    }

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

    return new UserResponse(
        saved.getId(),
        saved.getEmail(),
        saved.getRole(),
        saved.getStatus(),
        saved.getCreatedAt());
  }

  public UserResponse updateEmail(UpdateEmailRequest request) {
    User user = userRepository.findById(request.id())
        .orElseThrow(() -> new UserNotFoundException(request.id()));

    if (userRepository.existsByEmail(request.newEmail())) {
      throw new UserAlreadyExistsException(request.newEmail());
    }

    user.changeEmail(request.newEmail());
    User saved = userRepository.save(user);

    return new UserResponse(
        saved.getId(),
        saved.getEmail(),
        saved.getRole(),
        saved.getStatus(),
        saved.getCreatedAt());
  }
}
