package com.platform.uop.integrations.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.platform.uop.admin.dto.ChangeRoleRequest;
import com.platform.uop.admin.dto.LockUserRequest;
import com.platform.uop.admin.dto.UnlockUserRequest;
import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIT {

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

  private UUID adminId;
  private UUID userId;
  private UUID anotherUserId;
  private String adminSessionCookie;
  private String userSessionCookie;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    User admin = User.builder()
        .id(UUID.randomUUID())
        .email("admin@example.com")
        .passwordHash(passwordEncoder.encode("adminpass"))
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    adminId = userRepository.save(admin).getId();

    User user = User.builder()
        .id(UUID.randomUUID())
        .email("user@example.com")
        .passwordHash(passwordEncoder.encode("userpass"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    userId = userRepository.save(user).getId();

    User anotherUser = User.builder()
        .id(UUID.randomUUID())
        .email("another@example.com")
        .passwordHash(passwordEncoder.encode("anotherpass"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    anotherUserId = userRepository.save(anotherUser).getId();
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

  @BeforeEach
  void loginUsers() throws Exception {
    adminSessionCookie = loginAndGetSessionCookie("admin@example.com", "adminpass");
    userSessionCookie = loginAndGetSessionCookie("user@example.com", "userpass");
  }

  @Test
  void shouldGetUserAsAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/users/{id}", userId)
            .header("Cookie", adminSessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void shouldRejectGetUserAsRegularUser() throws Exception {
    mockMvc.perform(get("/api/admin/users/{id}", userId)
            .header("Cookie", userSessionCookie))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectGetUserWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/admin/users/{id}", userId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldGetAllUsersWithPagination() throws Exception {
    mockMvc.perform(get("/api/admin/users")
            .header("Cookie", adminSessionCookie)
            .param("page", "0")
            .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void shouldLockUserSuccessfully() throws Exception {
    LockUserRequest request = new LockUserRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/lock", userId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.status").value("LOCKED"));

    mockMvc.perform(get("/api/admin/users/{id}", userId)
            .header("Cookie", adminSessionCookie))
        .andExpect(jsonPath("$.status").value("LOCKED"));
  }

  @Test
  void shouldRejectLockAlreadyLockedUser() throws Exception {
    User lockedUser = User.builder()
        .id(UUID.randomUUID())
        .email("locked@example.com")
        .passwordHash(passwordEncoder.encode("password"))
        .role(UserRole.USER)
        .status(UserStatus.LOCKED)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID lockedUserId = userRepository.save(lockedUser).getId();

    LockUserRequest request = new LockUserRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/lock", lockedUserId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("User with id " + lockedUserId + " is already locked"));
  }

  @Test
  void shouldRejectLockAdminUser() throws Exception {
    LockUserRequest request = new LockUserRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/lock", adminId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot modify admin user"));
  }

  @Test
  void shouldUnlockUserSuccessfully() throws Exception {
    User lockedUser = User.builder()
        .id(UUID.randomUUID())
        .email("tounlock@example.com")
        .passwordHash(passwordEncoder.encode("password"))
        .role(UserRole.USER)
        .status(UserStatus.LOCKED)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID lockedUserId = userRepository.save(lockedUser).getId();

    UnlockUserRequest request = new UnlockUserRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/unlock", lockedUserId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(lockedUserId.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void shouldRejectUnlockNonLockedUser() throws Exception {
    UnlockUserRequest request = new UnlockUserRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/unlock", userId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("User with id " + userId + " is not locked"));
  }

  @Test
  void shouldRejectUnlockAdminUser() throws Exception {
    User lockedAdmin = User.builder()
        .id(UUID.randomUUID())
        .email("lockedadmin@example.com")
        .passwordHash(passwordEncoder.encode("password"))
        .role(UserRole.ADMIN)
        .status(UserStatus.LOCKED)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID lockedAdminId = userRepository.save(lockedAdmin).getId();

    UnlockUserRequest request = new UnlockUserRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/unlock", lockedAdminId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot modify admin user"));
  }

  @Test
  void shouldChangeRoleSuccessfully() throws Exception {
    ChangeRoleRequest request = new ChangeRoleRequest(UserRole.ADMIN);

    mockMvc.perform(patch("/api/admin/users/{id}/role", userId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void shouldRejectChangeRoleForAdmin() throws Exception {
    ChangeRoleRequest request = new ChangeRoleRequest(UserRole.USER);

    mockMvc.perform(patch("/api/admin/users/{id}/role", adminId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot modify admin user"));
  }

  @Test
  void shouldDeactivateUserSuccessfully() throws Exception {
    DeactivateAccountRequest request = new DeactivateAccountRequest();

    mockMvc.perform(delete("/api/admin/users/{id}", userId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/admin/users/{id}", userId)
            .header("Cookie", adminSessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.email").value("deleted_" + userId + "@deleted.local"));
  }

  @Test
  void shouldRejectDeactivateAlreadyInactiveUser() throws Exception {
    User inactiveUser = User.builder()
        .id(UUID.randomUUID())
        .email("inactive@example.com")
        .passwordHash(passwordEncoder.encode("password"))
        .role(UserRole.USER)
        .status(UserStatus.INACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    UUID inactiveUserId = userRepository.save(inactiveUser).getId();

    DeactivateAccountRequest request = new DeactivateAccountRequest();

    mockMvc.perform(delete("/api/admin/users/{id}", inactiveUserId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("User with id " + inactiveUserId + " is already deactivated"));
  }

  @Test
  void shouldRejectDeactivateAdminUser() throws Exception {
    DeactivateAccountRequest request = new DeactivateAccountRequest();

    mockMvc.perform(delete("/api/admin/users/{id}", adminId)
            .header("Cookie", adminSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot modify admin user"));
  }

  @Test
  void shouldRejectAdminOperationsAsRegularUser() throws Exception {
    LockUserRequest lockRequest = new LockUserRequest();
    UnlockUserRequest unlockRequest = new UnlockUserRequest();
    ChangeRoleRequest roleRequest = new ChangeRoleRequest(UserRole.ADMIN);
    DeactivateAccountRequest deactRequest = new DeactivateAccountRequest();

    mockMvc.perform(patch("/api/admin/users/{id}/lock", anotherUserId)
            .header("Cookie", userSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(lockRequest)))
        .andExpect(status().isForbidden());

    mockMvc.perform(patch("/api/admin/users/{id}/unlock", anotherUserId)
            .header("Cookie", userSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(unlockRequest)))
        .andExpect(status().isForbidden());

    mockMvc.perform(patch("/api/admin/users/{id}/role", anotherUserId)
            .header("Cookie", userSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(roleRequest)))
        .andExpect(status().isForbidden());

    mockMvc.perform(delete("/api/admin/users/{id}", anotherUserId)
            .header("Cookie", userSessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(deactRequest)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnNotFoundForNonExistentUser() throws Exception {
    UUID nonExistentId = UUID.randomUUID();

    mockMvc.perform(get("/api/admin/users/{id}", nonExistentId)
            .header("Cookie", adminSessionCookie))
        .andExpect(status().isNotFound());
  }
}
