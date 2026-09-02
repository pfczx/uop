package com.platform.uop.unit.service;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.setup.exception.SetupAlreadyCompletedException;
import com.platform.uop.setup.service.SetupService;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private SetupService setupService;

  @Nested
  @DisplayName("createAdmin")
  class CreateAdmin {

    @Test
    @DisplayName("Should create admin user with correct fields (role=ADMIN, status=ACTIVE, passwordHash from encoder)")
    void createAdmin_Success() {
      CreateAdminRequest request = new CreateAdminRequest("admin@platform.com", "AdminPass123!");
      when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
      when(passwordEncoder.encode("AdminPass123!")).thenReturn("encoded_admin_pass");
      when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

      SetupResponse response = setupService.createAdmin(request);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      User saved = captor.getValue();

      assertThat(saved.getEmail()).isEqualTo("admin@platform.com");
      assertThat(saved.getPasswordHash()).isEqualTo("encoded_admin_pass");
      assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
      assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getUpdatedAt()).isNotNull();

      assertThat(response.email()).isEqualTo("admin@platform.com");
      assertThat(response.role()).isEqualTo(UserRole.ADMIN);
      assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should assign the persisted id to SetupResponse when repo returns an id")
    void createAdmin_PersistsIdInResponse() {
      CreateAdminRequest request = new CreateAdminRequest("admin@platform.com", "AdminPass123!");
      UUID generatedId = UUID.randomUUID();
      when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
      when(passwordEncoder.encode("AdminPass123!")).thenReturn("encoded");
      when(userRepository.save(any(User.class))).thenAnswer(i -> {
        User u = i.getArgument(0);
        return User.builder()
            .id(generatedId)
            .email(u.getEmail())
            .passwordHash(u.getPasswordHash())
            .role(u.getRole())
            .status(u.getStatus())
            .createdAt(u.getCreatedAt())
            .updatedAt(u.getUpdatedAt())
            .build();
      });

      SetupResponse response = setupService.createAdmin(request);

      assertThat(response.id()).isEqualTo(generatedId);
    }

    @Test
    @DisplayName("Compute createdAt/updatedAt are set on the entity before save")
    void createAdmin_SetsTimestamps() {
      CreateAdminRequest request = new CreateAdminRequest("admin@platform.com", "AdminPass123!");
      Instant before = Instant.now().minusSeconds(5);
      when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
      when(passwordEncoder.encode("AdminPass123!")).thenReturn("encoded");
      when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

      setupService.createAdmin(request);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      assertThat(captor.getValue().getCreatedAt()).isAfterOrEqualTo(before);
      assertThat(captor.getValue().getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("Should throw SetupAlreadyCompletedException when an admin already exists")
    void createAdmin_AlreadyCompleted_Throws() {
      CreateAdminRequest request = new CreateAdminRequest("admin@platform.com", "AdminPass123!");
      when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

      assertThatThrownBy(() -> setupService.createAdmin(request))
          .isInstanceOf(SetupAlreadyCompletedException.class)
          .hasMessage("Setup already completed");

      verify(passwordEncoder, never()).encode(any());
      verify(userRepository, never()).save(any());
    }
  }
}
