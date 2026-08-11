package com.platform.uop.unit.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.platform.uop.users.bootstrap.AdminBootstrapProperties;
import com.platform.uop.users.bootstrap.AdminSeeder;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private AdminBootstrapProperties properties;

  @InjectMocks
  private AdminSeeder adminSeeder;

  @Nested
  @DisplayName("run")
  class Run {

    @Test
    @DisplayName("Should do nothing when seeder is disabled (enabled=false)")
    void run_Disabled_DoesNothing() {
      when(properties.enabled()).thenReturn(false);

      adminSeeder.run();

      verify(userRepository, never()).existsByRole(any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should do nothing when an admin already exists (admin already seeded)")
    void run_AdminAlreadyExists_DoesNothing() {
      when(properties.enabled()).thenReturn(true);
      when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

      adminSeeder.run();

      verify(passwordEncoder, never()).encode(any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should seed admin with email/password from properties, role=ADMIN, status=ACTIVE")
    void run_SeedsAdmin_WithCorrectFields() {
      when(properties.enabled()).thenReturn(true);
      when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
      when(properties.email()).thenReturn("admin@bootstrap.local");
      when(properties.password()).thenReturn("bootPass123!");
      when(passwordEncoder.encode("bootPass123!")).thenReturn("encodedBootPass");

      adminSeeder.run();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      User saved = captor.getValue();

      assertThat(saved.getEmail()).isEqualTo("admin@bootstrap.local");
      assertThat(saved.getPasswordHash()).isEqualTo("encodedBootPass");
      assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
      assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getUpdatedAt()).isNotNull();
    }
  }
}
