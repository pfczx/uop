package com.platform.uop.users.entity;

import java.time.Instant;
import java.util.UUID;

import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public void changeEmail(String newEmail) {
    this.email = newEmail;
    this.updatedAt = Instant.now();
  }

  public void changePassword(String password) {
    this.passwordHash = password;
    this.updatedAt = Instant.now();
  }

  public void deactivateAccount() {
    this.status = UserStatus.INACTIVE;
    this.updatedAt = Instant.now();
    this.email = "deleted_" + this.getId() + "@deleted.local";
  }

}
