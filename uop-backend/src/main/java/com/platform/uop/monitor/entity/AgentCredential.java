package com.platform.uop.monitor.entity;

import com.platform.uop.monitor.entity.Monitor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "agent_credentials", uniqueConstraints = {
    @UniqueConstraint(name = "uk_agent_credentials_monitor_id", columnNames = "monitor_id"),
    @UniqueConstraint(name = "uk_agent_credentials_registration_token_hash", columnNames = "registration_token_hash"),
    @UniqueConstraint(name = "uk_agent_credentials_credential_hash", columnNames = "credential_hash")
})
@NoArgsConstructor
public class AgentCredential {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "monitor_id", nullable = false)
  private Monitor monitor;

  @Column(name = "registration_token_hash", length = 64)
  private String registrationTokenHash;

  @Column(name = "registration_token_expires_at")
  private Instant registrationTokenExpiresAt;

  @Column(name = "credential_hash", length = 64)
  private String credentialHash;

  @Column(name = "credential_created_at")
  private Instant credentialCreatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public AgentCredential(Monitor monitor) {
    this.monitor = monitor;
  }

  public void setRegistrationToken(
      String tokenHash,
      Instant expiresAt) {
    this.registrationTokenHash = tokenHash;
    this.registrationTokenExpiresAt = expiresAt;
  }

  public boolean isRegistrationTokenValid(Instant now) {
    return registrationTokenHash != null
        && registrationTokenExpiresAt != null
        && registrationTokenExpiresAt.isAfter(now);
  }

  public void consumeRegistrationToken() {
    registrationTokenHash = null;
    registrationTokenExpiresAt = null;
  }

  public void setCredential(String credentialHash) {
    this.credentialHash = credentialHash;
    this.credentialCreatedAt = Instant.now();
  }

  public void revoke() {
    registrationTokenHash = null;
    registrationTokenExpiresAt = null;
    credentialHash = null;
    credentialCreatedAt = null;
  }

  public boolean isCredentialActive() {
    return credentialHash != null;
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
