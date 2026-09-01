package com.platform.uop.integrations.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;
import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.users.repository.UserRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupControllerIT {

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
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  void shouldCreateAdminSuccessfully() throws Exception {
    CreateAdminRequest request = new CreateAdminRequest("admin@test.com", "securePassword123");

    mockMvc.perform(post("/api/setup/admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").value("admin@test.com"))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void shouldRejectDuplicateAdminCreation() throws Exception {
    CreateAdminRequest request = new CreateAdminRequest("admin@test.com", "securePassword123");

    mockMvc.perform(post("/api/setup/admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/setup/admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Setup already completed"));
  }

  @Test
  void shouldRejectInvalidEmail() throws Exception {
    CreateAdminRequest request = new CreateAdminRequest("invalid-email", "securePassword123");

    mockMvc.perform(post("/api/setup/admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectBlankPassword() throws Exception {
    CreateAdminRequest request = new CreateAdminRequest("admin@test.com", "");

    mockMvc.perform(post("/api/setup/admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
