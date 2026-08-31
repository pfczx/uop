package com.platform.uop.integrations.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


import tools.jackson.databind.json.JsonMapper;
import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.dto.UpdateEmailRequest;
import com.platform.uop.users.dto.UpdatePasswordRequest;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIT {

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
  private MockMvc mockMvc;

  @Autowired
  private JsonMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private UUID userId;
  private String sessionCookie;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    User user = User.builder()
        .id(UUID.randomUUID())
        .email("test@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);
    userId = saved.getId();
  }

  private String loginAndGetSessionCookie(String email, String password) throws Exception {
    LoginRequest request = new LoginRequest(email, password);

    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andReturn();

    Cookie[] cookies = result.getResponse().getCookies();
    assertThat(cookies).isNotNull();
    for (Cookie cookie : cookies) {
      if ("JSESSIONID".equals(cookie.getName())) {
        return cookie.getName() + "=" + cookie.getValue();
      }
    }
    throw new IllegalStateException("JSESSIONID cookie not found");
  }

  @Test
  void shouldCreateUserSuccessfully() throws Exception {
    CreateUserRequest request = new CreateUserRequest("newuser@example.com", "newPassword123");

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void shouldRejectCreateUserWithDuplicateEmail() throws Exception {
    CreateUserRequest request = new CreateUserRequest("test@example.com", "newPassword123");

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("User with email test@example.com already exists"));
  }

  @Test
  void shouldRejectCreateUserWithInvalidEmail() throws Exception {
    CreateUserRequest request = new CreateUserRequest("invalid-email", "newPassword123");

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCreateUserWithBlankPassword() throws Exception {
    CreateUserRequest request = new CreateUserRequest("newuser@example.com", "");

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldUpdateEmailSuccessfully() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdateEmailRequest request = new UpdateEmailRequest("updated@example.com");

    mockMvc.perform(patch("/api/users/{id}/email", userId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("updated@example.com"))
        .andExpect(jsonPath("$.id").value(userId.toString()));
  }

  @Test
  void shouldRejectUpdateEmailToExistingEmail() throws Exception {
    User otherUser = User.builder()
        .id(UUID.randomUUID())
        .email("other@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    userRepository.save(otherUser);

    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdateEmailRequest request = new UpdateEmailRequest("other@example.com");

    mockMvc.perform(patch("/api/users/{id}/email", userId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("User with email other@example.com already exists"));
  }

  @Test
  void shouldRejectUpdateEmailForOtherUser() throws Exception {
    User otherUser = User.builder()
        .id(UUID.randomUUID())
        .email("other@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID otherUserId = userRepository.save(otherUser).getId();

    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdateEmailRequest request = new UpdateEmailRequest("new@example.com");

    mockMvc.perform(patch("/api/users/{id}/email", otherUserId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldUpdatePasswordSuccessfully() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdatePasswordRequest request = new UpdatePasswordRequest("password123", "newPassword456");

    mockMvc.perform(patch("/api/users/{id}/password", userId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()));

    sessionCookie = loginAndGetSessionCookie("test@example.com", "newPassword456");

    mockMvc.perform(get("/api/auth/me")
            .header("Cookie", sessionCookie))
        .andExpect(status().isOk());
  }

  @Test
  void shouldRejectUpdatePasswordWithWrongOldPassword() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdatePasswordRequest request = new UpdatePasswordRequest("wrongpassword", "newPassword456");

    mockMvc.perform(patch("/api/users/{id}/password", userId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectUpdatePasswordForOtherUser() throws Exception {
    User otherUser = User.builder()
        .id(UUID.randomUUID())
        .email("other@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID otherUserId = userRepository.save(otherUser).getId();

    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdatePasswordRequest request = new UpdatePasswordRequest("password123", "newPassword456");

    mockMvc.perform(patch("/api/users/{id}/password", otherUserId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldDeactivateAccountSuccessfully() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    DeactivateAccountRequest request = new DeactivateAccountRequest();

    mockMvc.perform(patch("/api/users/{id}/deactivate", userId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.email").value("deleted_" + userId + "@deleted.local"));

    mockMvc.perform(get("/api/auth/me")
            .header("Cookie", sessionCookie))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectDeactivateAccountForOtherUser() throws Exception {
    User otherUser = User.builder()
        .id(UUID.randomUUID())
        .email("other@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID otherUserId = userRepository.save(otherUser).getId();

    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    DeactivateAccountRequest request = new DeactivateAccountRequest();

    mockMvc.perform(patch("/api/users/{id}/deactivate", otherUserId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectOperationsWithoutAuthentication() throws Exception {
    UpdateEmailRequest emailRequest = new UpdateEmailRequest("new@example.com");
    UpdatePasswordRequest passRequest = new UpdatePasswordRequest("old", "new");
    DeactivateAccountRequest deactRequest = new DeactivateAccountRequest();

    mockMvc.perform(patch("/api/users/{id}/email", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(emailRequest)))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(patch("/api/users/{id}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(passRequest)))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(patch("/api/users/{id}/deactivate", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(deactRequest)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectUpdateEmailWithInvalidFormat() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    UpdateEmailRequest request = new UpdateEmailRequest("invalid-email");

    mockMvc.perform(patch("/api/users/{id}/email", userId)
            .header("Cookie", sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
