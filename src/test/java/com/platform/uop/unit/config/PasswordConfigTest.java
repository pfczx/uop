package com.platform.uop.unit.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.platform.uop.auth.config.PasswordConfig;

class PasswordConfigTest {

  @Test
  @DisplayName("Should create BCrypt PasswordEncoder instance that encodes and matches passwords correctly")
  void passwordEncoder_CreationAndFunctionality() {
    PasswordConfig passwordConfig = new PasswordConfig();
    PasswordEncoder encoder = passwordConfig.passwordEncoder();

    assertThat(encoder).isNotNull();

    String rawPassword = "mySecurePassword123";
    String encodedPassword = encoder.encode(rawPassword);

    assertThat(encodedPassword).isNotEqualTo(rawPassword);
    assertThat(encoder.matches(rawPassword, encodedPassword)).isTrue();
    assertThat(encoder.matches("wrongPassword", encodedPassword)).isFalse();
  }
}
