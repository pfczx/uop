package com.platform.uop.unit.monitor.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.platform.uop.monitor.entity.AgentCredential;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;

class AgentCredentialEntityTest {

  private static Workspace workspace(UUID id) {
    Workspace ws = new Workspace("Test Workspace", "Test Description");
    try {
      Field field = Workspace.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(ws, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return ws;
  }

  private static Monitor monitor(UUID id, Workspace workspace) {
    Monitor monitor = new Monitor(workspace,
        MonitorDescriptor.newBuilder().setId("desc-1").setName("m").build());
    try {
      Field field = Monitor.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(monitor, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return monitor;
  }

  private Instant nowPlusSeconds(long seconds) {
    return Instant.now().plusSeconds(seconds);
  }

  @Test
  @DisplayName("Should create agent credential with monitor and null timestamps")
  void create_SetsFields() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);

    AgentCredential credential = new AgentCredential(monitor);

    assertThat(credential.getMonitor()).isEqualTo(monitor);
    assertThat(credential.getId()).isNull();
    assertThat(credential.getRegistrationTokenHash()).isNull();
    assertThat(credential.getRegistrationTokenExpiresAt()).isNull();
    assertThat(credential.getCredentialHash()).isNull();
    assertThat(credential.getCredentialCreatedAt()).isNull();
    assertThat(credential.getCreatedAt()).isNull();
    assertThat(credential.getUpdatedAt()).isNull();
    assertThat(credential.isCredentialActive()).isFalse();
    assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("Should set registration token and expiry")
  void setRegistrationToken_SetsHashAndExpiry() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);
    Instant expiresAt = nowPlusSeconds(60);

    credential.setRegistrationToken("token-hash", expiresAt);

    assertThat(credential.getRegistrationTokenHash()).isEqualTo("token-hash");
    assertThat(credential.getRegistrationTokenExpiresAt()).isEqualTo(expiresAt);
    assertThat(credential.isRegistrationTokenValid(Instant.now())).isTrue();
  }

  @Test
  @DisplayName("Should report registration token invalid when hash is null")
  void isRegistrationTokenValid_NullHash_Invalid() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken(null, nowPlusSeconds(60));

    assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("Should report registration token invalid when expiry is null")
  void isRegistrationTokenValid_NullExpiry_Invalid() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken("token-hash", null);

    assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("Should report registration token invalid when expired")
  void isRegistrationTokenValid_Expired_Invalid() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken("token-hash", nowPlusSeconds(-60));

    assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("Should consume registration token by clearing it")
  void consumeRegistrationToken_ClearsToken() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken("token-hash", nowPlusSeconds(60));

    credential.consumeRegistrationToken();

    assertThat(credential.getRegistrationTokenHash()).isNull();
    assertThat(credential.getRegistrationTokenExpiresAt()).isNull();
    assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("Should set credential hash and created at")
  void setCredential_SetsHashAndCreatedAt() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);

    credential.setCredential("credential-hash");

    assertThat(credential.getCredentialHash()).isEqualTo("credential-hash");
    assertThat(credential.getCredentialCreatedAt()).isNotNull();
    assertThat(credential.isCredentialActive()).isTrue();
  }

  @Test
  @DisplayName("Should report credential inactive when hash is null")
  void isCredentialActive_NullHash_Inactive() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);

    assertThat(credential.isCredentialActive()).isFalse();
  }

  @Test
  @DisplayName("Should revoke all credential data")
  void revoke_ClearsEverything() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = monitor(UUID.randomUUID(), workspace);
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken("token-hash", nowPlusSeconds(60));
    credential.setCredential("credential-hash");

    credential.revoke();

    assertThat(credential.getRegistrationTokenHash()).isNull();
    assertThat(credential.getRegistrationTokenExpiresAt()).isNull();
    assertThat(credential.getCredentialHash()).isNull();
    assertThat(credential.getCredentialCreatedAt()).isNull();
    assertThat(credential.isCredentialActive()).isFalse();
    assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
  }
}
