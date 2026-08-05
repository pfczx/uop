package com.platform.uop.users.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.dto.UpdateEmailRequest;
import com.platform.uop.users.dto.UpdatePasswordRequest;
import com.platform.uop.users.dto.UserResponse;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.exception.UserAlreadyExistsException;
import com.platform.uop.users.exception.UserNotFoundException;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  private UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt());
  }

  public UserResponse create(CreateUserRequest request) {
    User exists = userRepository.findByEmail(request.email())
        .orElse(null);

    if (exists != null && exists.getStatus() == UserStatus.ACTIVE) {
      throw new UserAlreadyExistsException(request.email());
    }

    User user = User.builder()
        .id(UUID.randomUUID())
        .email(request.email())
        .passwordHash(passwordEncoder.encode(request.password()))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);

    return toResponse(saved);
  }

  public UserResponse updateEmail(UUID id, UpdateEmailRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));

    if (userRepository.existsByEmail(request.newEmail())) {
      throw new UserAlreadyExistsException(request.newEmail());
    }

    user.changeEmail(request.newEmail());
    User saved = userRepository.save(user);

    return toResponse(saved);
  }

  // TODO: move pass change to \auth
  public UserResponse updatePassword(UUID id, UpdatePasswordRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));

    if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
      throw new BadCredentialsException("Wrong password");
    }

    user.changePassword(passwordEncoder.encode(request.Newpassword()));
    User saved = userRepository.save(user);

    return toResponse(saved);

  }

  public UserResponse deactivateAccount(UUID id, DeactivateAccountRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));

    user.deactivateAccount();
    User saved = userRepository.save(user);

    return toResponse(saved);

  }
}
