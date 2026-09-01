package com.platform.uop.integrations.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

@Testcontainers
@SpringBootTest
class UserRepositoryQueryIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:17"))
      .withDatabaseName("uop")
      .withUsername("postgres")
      .withPassword("postgres");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired
  private UserRepository userRepository;

  private UUID userId;
  private UUID adminId;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    User user = User.builder()
        .id(UUID.randomUUID())
        .email("user@example.com")
        .passwordHash("hashed-password")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    userId = userRepository.save(user).getId();

    User admin = User.builder()
        .id(UUID.randomUUID())
        .email("admin@example.com")
        .passwordHash("hashed-password")
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    adminId = userRepository.save(admin).getId();
  }

  @Test
  void shouldFindByEmail() {
    var found = userRepository.findByEmail("user@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(userId);
    assertThat(found.get().getEmail()).isEqualTo("user@example.com");
  }

  @Test
  void shouldReturnEmptyWhenEmailNotFound() {
    var found = userRepository.findByEmail("nonexistent@example.com");

    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnTrueForExistsByEmail() {
    boolean exists = userRepository.existsByEmail("user@example.com");

    assertThat(exists).isTrue();
  }

  @Test
  void shouldReturnFalseForExistsByEmailWhenNotFound() {
    boolean exists = userRepository.existsByEmail("nonexistent@example.com");

    assertThat(exists).isFalse();
  }

  @Test
  void shouldReturnTrueForExistsByRoleAdmin() {
    boolean exists = userRepository.existsByRole(UserRole.ADMIN);

    assertThat(exists).isTrue();
  }

  @Test
  void shouldReturnFalseForExistsByRoleAdminWhenNone() {
    userRepository.deleteById(adminId);

    boolean exists = userRepository.existsByRole(UserRole.ADMIN);

    assertThat(exists).isFalse();
  }

  @Test
  void shouldReturnTrueForExistsByRoleUser() {
    boolean exists = userRepository.existsByRole(UserRole.USER);

    assertThat(exists).isTrue();
  }

  @Test
  void shouldReturnTrueForExistsByEmailAndIdNotWhenEmailExistsForOtherUser() {
    boolean exists = userRepository.existsByEmailAndIdNot("admin@example.com", userId);

    assertThat(exists).isTrue();
  }

  @Test
  void shouldReturnFalseForExistsByEmailAndIdNotWhenEmailBelongsToSameUser() {
    boolean exists = userRepository.existsByEmailAndIdNot("user@example.com", userId);

    assertThat(exists).isFalse();
  }

  @Test
  void shouldReturnFalseForExistsByEmailAndIdNotWhenEmailDoesNotExist() {
    boolean exists = userRepository.existsByEmailAndIdNot("nonexistent@example.com", userId);

    assertThat(exists).isFalse();
  }

  @Test
  void shouldFindAllWithPagination() {
    var page = userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(2);
  }

  @Test
  void shouldSaveAndUpdateUser() {
    User user = User.builder()
        .id(UUID.randomUUID())
        .email("newuser@example.com")
        .passwordHash("hashed-password")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);

    assertThat(saved.getId()).isEqualTo(user.getId());
    assertThat(saved.getEmail()).isEqualTo("newuser@example.com");

    saved.changeEmail("updated@example.com");
    User updated = userRepository.saveAndFlush(saved);

    assertThat(updated.getEmail()).isEqualTo("updated@example.com");
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(saved.getUpdatedAt());
  }

  @Test
  void shouldDeleteUser() {
    userRepository.deleteById(userId);

    assertThat(userRepository.findById(userId)).isEmpty();
    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldEnforceUniqueEmailConstraint() {
    User duplicate = User.builder()
        .id(UUID.randomUUID())
        .email("user@example.com")
        .passwordHash("hashed-password")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}