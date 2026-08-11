package com.platform.uop.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import com.platform.uop.users.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private UserService userService;

  private static User existingUser(UUID id, UserRole role, UserStatus status) {
    return User.builder()
        .id(id)
        .email("user@example.com")
        .passwordHash("old_hash")
        .role(role)
        .status(status)
        .createdAt(Instant.now().minusSeconds(60))
        .updatedAt(Instant.now().minusSeconds(60))
        .build();
  }

  private static User existingUser(UserRole role, UserStatus status) {
    return existingUser(UUID.randomUUID(), role, status);
  }

  private static User persistedAfter(User input) {
    return input;
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("Should create user when email does not exist")
    void create_Success() {
      CreateUserRequest request = new CreateUserRequest(
          "test@example.com",
          "SecretPass123!");

      when(userRepository.findByEmail(request.email()))
          .thenReturn(Optional.empty());

      when(passwordEncoder.encode(request.password()))
          .thenReturn("encoded_pass");

      when(userRepository.save(any(User.class)))
          .thenAnswer(i -> persistedAfter(i.getArgument(0)));

      UserResponse response = userService.create(request);

      assertThat(response.email())
          .isEqualTo("test@example.com");

      assertThat(response.role())
          .isEqualTo(UserRole.USER);

      assertThat(response.status())
          .isEqualTo(UserStatus.ACTIVE);

      assertThat(response.id())
          .isNotNull();

      verify(userRepository)
          .findByEmail("test@example.com");

      verify(passwordEncoder)
          .encode("SecretPass123!");

      verify(userRepository)
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when email already exists")
    void create_UserWithSameEmailExists_Throws() {
      CreateUserRequest request = new CreateUserRequest(
          "user@example.com",
          "SecretPass123!");

      User existingUser = existingUser(
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findByEmail(request.email()))
          .thenReturn(Optional.of(existingUser));

      assertThatThrownBy(() -> userService.create(request))
          .isInstanceOf(UserAlreadyExistsException.class)
          .hasMessage(
              "User with email 'user@example.com' already exists");

      verify(userRepository)
          .findByEmail("user@example.com");

      verify(userRepository, never())
          .save(any(User.class));

      verify(passwordEncoder, never())
          .encode(anyString());
    }

    @Test
    @DisplayName("Should not encode password when email already exists")
    void create_EmailExists_DoesNotEncodePassword() {
      CreateUserRequest request = new CreateUserRequest(
          "user@example.com",
          "SecretPass123!");

      User existingUser = existingUser(
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findByEmail(request.email()))
          .thenReturn(Optional.of(existingUser));

      assertThatThrownBy(() -> userService.create(request))
          .isInstanceOf(UserAlreadyExistsException.class);

      verify(passwordEncoder, never())
          .encode(anyString());
    }
  }

  @Nested
  @DisplayName("updateEmail")
  class UpdateEmail {

    @Test
    @DisplayName("Should update email when user exists and new email is free")
    void updateEmail_Success() {
      UUID id = UUID.randomUUID();

      UpdateEmailRequest request = new UpdateEmailRequest("new@example.com");

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(userRepository.existsByEmailAndIdNot(
          "new@example.com",
          id))
          .thenReturn(false);

      when(userRepository.save(any(User.class)))
          .thenAnswer(i -> persistedAfter(i.getArgument(0)));

      UserResponse response = userService.updateEmail(id, request);

      assertThat(response.email())
          .isEqualTo("new@example.com");

      assertThat(response.id())
          .isEqualTo(id);

      verify(userRepository)
          .findById(id);

      verify(userRepository)
          .existsByEmailAndIdNot(
              "new@example.com",
              id);

      verify(userRepository)
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user does not exist")
    void updateEmail_UserNotFound_Throws() {
      UUID id = UUID.randomUUID();

      when(userRepository.findById(id))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.updateEmail(
          id,
          new UpdateEmailRequest(
              "new@example.com")))
          .isInstanceOf(UserNotFoundException.class);

      verify(userRepository, never())
          .existsByEmailAndIdNot(
              anyString(),
              any(UUID.class));

      verify(userRepository, never())
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when email belongs to another user")
    void updateEmail_EmailTaken_Throws() {
      UUID id = UUID.randomUUID();

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(userRepository.existsByEmailAndIdNot(
          "taken@example.com",
          id))
          .thenReturn(true);

      assertThatThrownBy(() -> userService.updateEmail(
          id,
          new UpdateEmailRequest(
              "taken@example.com")))
          .isInstanceOf(UserAlreadyExistsException.class)
          .hasMessage(
              "User with email 'taken@example.com' already exists");

      verify(userRepository)
          .existsByEmailAndIdNot(
              "taken@example.com",
              id);

      verify(userRepository, never())
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should allow updating email of a LOCKED user")
    void updateEmail_LockedUser_Succeeds() {
      UUID id = UUID.randomUUID();

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.LOCKED);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(userRepository.existsByEmailAndIdNot(
          "new@example.com",
          id))
          .thenReturn(false);

      when(userRepository.save(any(User.class)))
          .thenAnswer(i -> persistedAfter(i.getArgument(0)));

      UserResponse response = userService.updateEmail(
          id,
          new UpdateEmailRequest(
              "new@example.com"));

      assertThat(response.email())
          .isEqualTo("new@example.com");

      assertThat(response.status())
          .isEqualTo(UserStatus.LOCKED);

      verify(userRepository)
          .save(any(User.class));
    }
  }

  @Nested
  @DisplayName("updatePassword")
  class UpdatePassword {

    @Test
    @DisplayName("Should update password when old password matches")
    void updatePassword_Success() {
      UUID id = UUID.randomUUID();

      UpdatePasswordRequest request = new UpdatePasswordRequest(
          "NewSecret123!",
          "OldSecret123!");

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(passwordEncoder.matches(
          "OldSecret123!",
          "old_hash"))
          .thenReturn(true);

      when(passwordEncoder.encode(
          "NewSecret123!"))
          .thenReturn("new_hash");

      when(userRepository.save(any(User.class)))
          .thenAnswer(i -> persistedAfter(i.getArgument(0)));

      UserResponse response = userService.updatePassword(
          id,
          request);

      assertThat(user.getPasswordHash())
          .isEqualTo("new_hash");

      assertThat(response)
          .isNotNull();

      assertThat(response.id())
          .isEqualTo(id);

      verify(passwordEncoder)
          .matches(
              "OldSecret123!",
              "old_hash");

      verify(passwordEncoder)
          .encode("NewSecret123!");

      verify(userRepository)
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user does not exist")
    void updatePassword_UserNotFound_Throws() {
      UUID id = UUID.randomUUID();

      when(userRepository.findById(id))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.updatePassword(
          id,
          new UpdatePasswordRequest(
              "NewPassword",
              "OldPassword")))
          .isInstanceOf(UserNotFoundException.class);

      verify(passwordEncoder, never())
          .matches(anyString(), anyString());

      verify(userRepository, never())
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when old password does not match")
    void updatePassword_BadCredentials_Throws() {
      UUID id = UUID.randomUUID();

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(passwordEncoder.matches(
          "WrongOldPass!",
          "old_hash"))
          .thenReturn(false);

      assertThatThrownBy(() -> userService.updatePassword(
          id,
          new UpdatePasswordRequest(
              "NewSecret123!",
              "WrongOldPass!")))
          .isInstanceOf(BadCredentialsException.class)
          .hasMessage("Wrong password");

      verify(passwordEncoder)
          .matches(
              "WrongOldPass!",
              "old_hash");

      verify(passwordEncoder, never())
          .encode(anyString());

      verify(userRepository, never())
          .save(any(User.class));
    }
  }

  @Nested
  @DisplayName("deactivateAccount")
  class DeactivateAccount {

    @Test
    @DisplayName("Should deactivate account, set INACTIVE and anonymize email")
    void deactivateAccount_Success() {
      UUID id = UUID.randomUUID();

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.ACTIVE);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(userRepository.save(any(User.class)))
          .thenAnswer(i -> persistedAfter(i.getArgument(0)));

      UserResponse response = userService.deactivateAccount(
          id,
          new DeactivateAccountRequest());

      assertThat(response.status())
          .isEqualTo(UserStatus.INACTIVE);

      assertThat(response.email())
          .isEqualTo(
              "deleted_" + id + "@deleted.local");

      verify(userRepository)
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user does not exist")
    void deactivateAccount_UserNotFound_Throws() {
      UUID id = UUID.randomUUID();

      when(userRepository.findById(id))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.deactivateAccount(
          id,
          new DeactivateAccountRequest()))
          .isInstanceOf(UserNotFoundException.class);

      verify(userRepository, never())
          .save(any(User.class));
    }

    @Test
    @DisplayName("Should deactivate a LOCKED user")
    void deactivateAccount_LockedUser_Succeeds() {
      UUID id = UUID.randomUUID();

      User user = existingUser(
          id,
          UserRole.USER,
          UserStatus.LOCKED);

      when(userRepository.findById(id))
          .thenReturn(Optional.of(user));

      when(userRepository.save(any(User.class)))
          .thenAnswer(i -> persistedAfter(i.getArgument(0)));

      UserResponse response = userService.deactivateAccount(
          id,
          new DeactivateAccountRequest());

      assertThat(response.status())
          .isEqualTo(UserStatus.INACTIVE);

      assertThat(response.email())
          .isEqualTo(
              "deleted_" + id + "@deleted.local");

      verify(userRepository)
          .save(any(User.class));
    }
  }
}
