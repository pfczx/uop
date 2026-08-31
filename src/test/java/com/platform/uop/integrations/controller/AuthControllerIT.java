package com.platform.uop.integrations.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

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
        .createdAt(java.time.Instant.now())
        .updatedAt(java.time.Instant.now())
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
  void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
    LoginRequest request = new LoginRequest("test@example.com", "password123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.role").value("USER"));
  }

  @Test
  void shouldRejectLoginWithWrongPassword() throws Exception {
    LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectLoginWithNonExistentUser() throws Exception {
    LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectLoginForLockedUser() throws Exception {
    User lockedUser = User.builder()
        .id(UUID.randomUUID())
        .email("locked@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.LOCKED)
        .createdAt(java.time.Instant.now())
        .updatedAt(java.time.Instant.now())
        .build();
    userRepository.save(lockedUser);

    LoginRequest request = new LoginRequest("locked@example.com", "password123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectLoginForInactiveUser() throws Exception {
    User inactiveUser = User.builder()
        .id(UUID.randomUUID())
        .email("inactive@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.INACTIVE)
        .createdAt(java.time.Instant.now())
        .updatedAt(java.time.Instant.now())
        .build();
    userRepository.save(inactiveUser);

    LoginRequest request = new LoginRequest("inactive@example.com", "password123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnCurrentUserWithValidSession() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    mockMvc.perform(get("/api/auth/me")
            .header("Cookie", sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void shouldRejectMeWithoutSession() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectMeWithInvalidSession() throws Exception {
    mockMvc.perform(get("/api/auth/me")
            .header("Cookie", "JSESSIONID=invalid-session-id"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldLogoutSuccessfully() throws Exception {
    sessionCookie = loginAndGetSessionCookie("test@example.com", "password123");

    mockMvc.perform(post("/api/auth/logout")
            .header("Cookie", sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("User logged out successfully."));

    mockMvc.perform(get("/api/auth/me")
            .header("Cookie", sessionCookie))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectLoginWithInvalidEmailFormat() throws Exception {
    LoginRequest request = new LoginRequest("invalid-email", "password123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectLoginWithBlankPassword() throws Exception {
    LoginRequest request = new LoginRequest("test@example.com", "");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldAllowAdminLogin() throws Exception {
    User admin = User.builder()
        .id(UUID.randomUUID())
        .email("admin@example.com")
        .passwordHash(passwordEncoder.encode("adminpass"))
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(java.time.Instant.now())
        .updatedAt(java.time.Instant.now())
        .build();
    userRepository.save(admin);

    LoginRequest request = new LoginRequest("admin@example.com", "adminpass");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }
}
