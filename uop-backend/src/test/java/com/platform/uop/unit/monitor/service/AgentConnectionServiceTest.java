package com.platform.uop.unit.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.PlatformMessage;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import com.platform.uop.monitor.service.AgentConnectionService;

class AgentConnectionServiceTest {

  private static StreamObserver<PlatformMessage> capturingObserver() {
    return new StreamObserver<>() {
      @Override
      public void onNext(PlatformMessage message) {
      }

      @Override
      public void onError(Throwable throwable) {
      }

      @Override
      public void onCompleted() {
      }
    };
  }

  @Nested
  @DisplayName("openConnection")
  class OpenConnection {

    @Test
    @DisplayName("Should open a new connection for a monitor")
    void openConnection_NewSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();

      AgentConnectionService.AgentSession session =
          service.openConnection(monitorId, capturingObserver());

      assertThat(session.monitorId()).isEqualTo(monitorId);
      assertThat(session.isOpen()).isTrue();
      assertThat(service.isConnected(monitorId)).isTrue();
    }

    @Test
    @DisplayName("Should close previous session when a new connection replaces it")
    void openConnection_ReplacesPreviousSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();

      AgentConnectionService.AgentSession first =
          service.openConnection(monitorId, capturingObserver());
      AgentConnectionService.AgentSession second =
          service.openConnection(monitorId, capturingObserver());

      assertThat(first.isOpen()).isFalse();
      assertThat(second.isOpen()).isTrue();
      assertThat(service.isConnected(monitorId)).isTrue();
    }
  }

  @Nested
  @DisplayName("closeConnection")
  class CloseConnection {

    @Test
    @DisplayName("Should close an existing session by identity")
    void closeConnection_ExistingSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();

      AgentConnectionService.AgentSession session =
          service.openConnection(monitorId, capturingObserver());

      service.closeConnection(session);

      assertThat(service.isConnected(monitorId)).isFalse();
    }

    @Test
    @DisplayName("Should do nothing when session is not tracked")
    void closeConnection_UntrackedSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();
      AgentConnectionService.AgentSession session =
          service.openConnection(monitorId, capturingObserver());
      service.closeConnection(session);

      AgentConnectionService.AgentSession another =
          service.openConnection(monitorId, capturingObserver());
      service.closeConnection(another);

      assertThat(service.isConnected(monitorId)).isFalse();
    }
  }

  @Nested
  @DisplayName("isConnected")
  class IsConnected {

    @Test
    @DisplayName("Should return false when no session exists")
    void isConnected_NoSession() {
      AgentConnectionService service = new AgentConnectionService();

      assertThat(service.isConnected(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Should return false when session is closed")
    void isConnected_ClosedSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();

      AgentConnectionService.AgentSession session =
          service.openConnection(monitorId, capturingObserver());
      session.close(Status.UNAVAILABLE.asRuntimeException());

      assertThat(service.isConnected(monitorId)).isFalse();
    }
  }

  @Nested
  @DisplayName("sendDescriptor")
  class SendDescriptor {

    @Test
    @DisplayName("Should send descriptor and return true when connected")
    void sendDescriptor_Connected() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();
      MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
          .setId("desc-1")
          .setName("Test Monitor")
          .build();

      service.openConnection(monitorId, capturingObserver());

      boolean sent = service.sendDescriptor(monitorId, descriptor);

      assertThat(sent).isTrue();
    }

    @Test
    @DisplayName("Should return false when not connected")
    void sendDescriptor_NotConnected() {
      AgentConnectionService service = new AgentConnectionService();
      MonitorDescriptor descriptor = MonitorDescriptor.newBuilder().build();

      assertThat(service.sendDescriptor(UUID.randomUUID(), descriptor)).isFalse();
    }

    @Test
    @DisplayName("Should return false when session is closed")
    void sendDescriptor_ClosedSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();
      MonitorDescriptor descriptor = MonitorDescriptor.newBuilder().build();

      AgentConnectionService.AgentSession session =
          service.openConnection(monitorId, capturingObserver());
      session.close(Status.UNAVAILABLE.asRuntimeException());

      assertThat(service.sendDescriptor(monitorId, descriptor)).isFalse();
    }
  }

  @Nested
  @DisplayName("closeMonitorConnection")
  class CloseMonitorConnection {

    @Test
    @DisplayName("Should remove and close session for monitor")
    void closeMonitorConnection_ExistingSession() {
      AgentConnectionService service = new AgentConnectionService();
      UUID monitorId = UUID.randomUUID();

      AgentConnectionService.AgentSession session =
          service.openConnection(monitorId, capturingObserver());

      service.closeMonitorConnection(monitorId);

      assertThat(session.isOpen()).isFalse();
      assertThat(service.isConnected(monitorId)).isFalse();
    }

    @Test
    @DisplayName("Should do nothing when no session exists")
    void closeMonitorConnection_NoSession() {
      AgentConnectionService service = new AgentConnectionService();

      service.closeMonitorConnection(UUID.randomUUID());
    }
  }
}
