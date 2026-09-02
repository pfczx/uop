package com.platform.uop.unit.entity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;

class UserTest {

  private static User user() {
    return User.builder()
        .id(UUID.randomUUID())
        .email("user@example.com")
        .passwordHash("old_hash")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now().minusSeconds(60))
        .updatedAt(Instant.now().minusSeconds(60))
        .build();
  }

  @Test
  @DisplayName("Should update email and advance updatedAt")
  void changeEmail_UpdatesFields() {
    User user = user();
    Instant beforeUpdatedAt = user.getUpdatedAt();

    user.changeEmail("new@example.com");

    assertThat(user.getEmail()).isEqualTo("new@example.com");
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }

  @Test
  @DisplayName("Should update passwordHash and advance updatedAt")
  void changePassword_UpdatesFields() {
    User user = user();
    Instant beforeUpdatedAt = user.getUpdatedAt();

    user.changePassword("new_hash");

    assertThat(user.getPasswordHash()).isEqualTo("new_hash");
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }

  @Test
  @DisplayName("Should set status to LOCKED and advance updatedAt")
  void lock_SetsLocked() {
    User user = user();
    Instant beforeUpdatedAt = user.getUpdatedAt();

    user.lock();

    assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }

  @Test
  @DisplayName("Should set status to ACTIVE and advance updatedAt")
  void unlock_SetsActive() {
    User user = user();
    user.lock();
    Instant beforeUpdatedAt = user.getUpdatedAt();
    try {
      Thread.sleep(2);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }

    user.unlock();

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }

  @Test
  @DisplayName("Should update role and advance updatedAt")
  void changeRole_UpdatesFields() {
    User user = user();
    Instant beforeUpdatedAt = user.getUpdatedAt();

    user.changeRole(UserRole.ADMIN);

    assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }

  @Test
  @DisplayName("Should set INACTIVE, anonymize email to deleted_<id>@deleted.local, and advance updatedAt")
  void deactivateAccount_UpdatesFields() {
    User user = user();
    UUID id = user.getId();
    Instant beforeUpdatedAt = user.getUpdatedAt();

    user.deactivateAccount();

    assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    assertThat(user.getEmail()).isEqualTo("deleted_" + id + "@deleted.local");
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }

  @Test
  @DisplayName("activate should set ACTIVE and advance updatedAt")
  void activate_SetsActive() {
    User user = user();
    user.lock();
    Instant beforeUpdatedAt = user.getUpdatedAt();
    try {
      Thread.sleep(2);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }

    user.activate();

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
  }
}
