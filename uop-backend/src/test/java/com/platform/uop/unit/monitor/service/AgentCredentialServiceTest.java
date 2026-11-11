package com.platform.uop.unit.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.platform.uop.monitor.entity.AgentCredential;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.AgentCredentialRepository;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.proto.v1.MonitorDescriptor;

@ExtendWith(MockitoExtension.class)
class AgentCredentialServiceTest {

  @Mock
  private AgentCredentialRepository credentialRepository;

  @Mock
  private MonitorRepository monitorRepository;

  private AgentCredentialService service;

  private static Monitor monitor(UUID id) {
    Monitor m = new Monitor(null,
        MonitorDescriptor.newBuilder().setId("desc-1").setName("m").build());
    try {
      java.lang.reflect.Field field = Monitor.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(m, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return m;
  }

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    service = new AgentCredentialService(credentialRepository, monitorRepository,
        Duration.ofMinutes(15));
  }

  @Nested
  @DisplayName("createRegistrationToken")
  class CreateRegistrationToken {

    @Test
    @DisplayName("Should create token when monitor exists")
    void createRegistrationToken_Success() {
      UUID monitorId = UUID.randomUUID();
      Monitor monitor = monitor(monitorId);
      AgentCredential existing = new AgentCredential(monitor);

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));
      when(credentialRepository.findByMonitorIdForUpdate(monitorId))
          .thenReturn(Optional.of(existing));

      String result = service.createRegistrationToken(monitorId);

      assertThat(result).isNotNull().isNotEmpty();
      assertThat(existing.getRegistrationTokenHash()).isNotNull();
      assertThat(existing.getRegistrationTokenExpiresAt()).isNotNull();
      assertThat(existing.isRegistrationTokenValid(Instant.now())).isTrue();
      verify(credentialRepository).save(existing);
    }

    @Test
    @DisplayName("Should create new credential when none exists for monitor")
    void createRegistrationToken_NoExistingCredential_CreatesNew() {
      UUID monitorId = UUID.randomUUID();
      Monitor monitor = monitor(monitorId);

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));
      when(credentialRepository.findByMonitorIdForUpdate(monitorId))
          .thenReturn(Optional.empty());

      String result = service.createRegistrationToken(monitorId);

      assertThat(result).isNotNull();
      verify(credentialRepository).save(any(AgentCredential.class));
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void createRegistrationToken_MonitorNotFound_Throws() {
      UUID monitorId = UUID.randomUUID();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.createRegistrationToken(monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");

      verify(credentialRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("registerAgent")
  class RegisterAgent {

    @Test
    @DisplayName("Should register agent and issue credential when token is valid")
    void registerAgent_Success() {
      UUID monitorId = UUID.randomUUID();
      String registrationToken = "registration-token";
      AgentCredential credential = new AgentCredential(monitor(monitorId));
      credential.setRegistrationToken("hash-of-token", Instant.now().plusSeconds(60));

      when(credentialRepository.findByRegistrationTokenHashForUpdate(anyString()))
          .thenReturn(Optional.of(credential));

      AgentCredentialService.RegisteredAgent result = service.registerAgent(registrationToken);

      assertThat(result.monitorId()).isEqualTo(monitorId);
      assertThat(result.credential()).isNotNull();
      assertThat(credential.isRegistrationTokenValid(Instant.now())).isFalse();
      assertThat(credential.isCredentialActive()).isTrue();
      verify(credentialRepository).save(credential);
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when token is blank")
    void registerAgent_BlankToken_Throws() {
      assertThatThrownBy(() -> service.registerAgent(""))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid registration token");

      assertThatThrownBy(() -> service.registerAgent("   "))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid registration token");

      assertThatThrownBy(() -> service.registerAgent(null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid registration token");

      verify(credentialRepository, never()).findByRegistrationTokenHashForUpdate(any());
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when token does not exist")
    void registerAgent_UnknownToken_Throws() {
      when(credentialRepository.findByRegistrationTokenHashForUpdate(any()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.registerAgent("unknown-token"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid registration token");

      verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when token has expired")
    void registerAgent_ExpiredToken_Throws() {
      UUID monitorId = UUID.randomUUID();
      AgentCredential credential = new AgentCredential(monitor(monitorId));
      credential.setRegistrationToken("hash-of-token", Instant.now().minusSeconds(60));

      when(credentialRepository.findByRegistrationTokenHashForUpdate(anyString()))
          .thenReturn(Optional.of(credential));

      assertThatThrownBy(() -> service.registerAgent("registration-token"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Registration token has expired");

      verify(credentialRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("authenticate")
  class Authenticate {

    @Test
    @DisplayName("Should return monitor id when credential is valid")
    void authenticate_Success() {
      UUID monitorId = UUID.randomUUID();
      String agentCredential = "agent-credential";
      AgentCredential credential = new AgentCredential(monitor(monitorId));
      credential.setCredential("hash-of-credential");

      when(credentialRepository.findByCredentialHash(anyString()))
          .thenReturn(Optional.of(credential));

      UUID result = service.authenticate(agentCredential);

      assertThat(result).isEqualTo(monitorId);
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when credential is blank")
    void authenticate_BlankCredential_Throws() {
      assertThatThrownBy(() -> service.authenticate(""))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid agent credential");

      assertThatThrownBy(() -> service.authenticate(null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid agent credential");

      verify(credentialRepository, never()).findByCredentialHash(any());
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when credential not found")
    void authenticate_NotFound_Throws() {
      when(credentialRepository.findByCredentialHash(any())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.authenticate("agent-credential"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Invalid agent credential");
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when credential has been revoked")
    void authenticate_Revoked_Throws() {
      UUID monitorId = UUID.randomUUID();
      AgentCredential credential = new AgentCredential(monitor(monitorId));
      // credential hash is null => revoked

      when(credentialRepository.findByCredentialHash(anyString()))
          .thenReturn(Optional.of(credential));

      assertThatThrownBy(() -> service.authenticate("agent-credential"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Agent credential has been revoked");
    }
  }

  @Nested
  @DisplayName("revokeAgentCredentials")
  class RevokeAgentCredentials {

    @Test
    @DisplayName("Should revoke credentials when they exist for monitor")
    void revokeAgentCredentials_Success() {
      UUID monitorId = UUID.randomUUID();
      AgentCredential credential = new AgentCredential(monitor(monitorId));
      credential.setCredential("credential-hash");

      when(credentialRepository.findByMonitorIdForUpdate(monitorId))
          .thenReturn(Optional.of(credential));

      service.revokeAgentCredentials(monitorId);

      assertThat(credential.isCredentialActive()).isFalse();
      assertThat(credential.getRegistrationTokenHash()).isNull();
      verify(credentialRepository).save(credential);
    }

    @Test
    @DisplayName("Should do nothing when no credentials exist for monitor")
    void revokeAgentCredentials_NoCredential_DoesNothing() {
      UUID monitorId = UUID.randomUUID();

      when(credentialRepository.findByMonitorIdForUpdate(monitorId))
          .thenReturn(Optional.empty());

      service.revokeAgentCredentials(monitorId);

      verify(credentialRepository, never()).save(any());
    }
  }
}
