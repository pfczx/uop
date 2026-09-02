package com.platform.uop.integrations.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

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
class UserRepositoryIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:17"))
      .withDatabaseName("uop")
      .withUsername("postgres")
      .withPassword("postgres");;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired
  private UserRepository userRepository;

  @Test
  void shouldSaveAndFindUserById() {
    UUID id = UUID.randomUUID();

    User user = User.builder()
        .id(id)
        .email("test@example.com")
        .passwordHash("hashed-password")
        .status(UserStatus.ACTIVE)
        .role(UserRole.USER)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);

    assertThat(saved.getId()).isEqualTo(id);

    User found = userRepository.findById(id)
        .orElseThrow();

    assertThat(found.getEmail()).isEqualTo("test@example.com");
    assertThat(found.getPasswordHash()).isEqualTo("hashed-password");
    assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(found.getRole()).isEqualTo(UserRole.USER);
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }
}
