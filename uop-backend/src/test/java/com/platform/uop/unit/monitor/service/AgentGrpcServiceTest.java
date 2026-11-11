package com.platform.uop.unit.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentConnectionService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.monitor.service.AgentGrpcService;
import com.platform.uop.observation.service.ObservationService;
import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AuthenticationRequest;
import com.platform.uop.proto.v1.DescriptorApplied;
import com.platform.uop.proto.v1.Heartbeat;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.PlatformMessage;
import com.platform.uop.proto.v1.RegistrationAccepted;
import com.platform.uop.proto.v1.RegistrationRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

@ExtendWith(MockitoExtension.class)
class AgentGrpcServiceTest {

  @Mock
  private AgentConnectionService agentConnectionService;

  @Mock
  private AgentCredentialService agentCredentialService;

  @Mock
  private MonitorRepository monitorRepository;

  @Mock
  private ObservationService observationService;

  @InjectMocks
  private AgentGrpcService agentGrpcService;

  private final AgentConnectionService realConnectionService = new AgentConnectionService();

  private static UUID monitorId() {
    return UUID.randomUUID();
  }

  private static Monitor monitor(UUID id) {
    Monitor m = new Monitor(null,
        com.platform.uop.proto.v1.MonitorDescriptor.newBuilder()
            .setId("desc-1")
            .setName("Test Monitor")
            .setDescription("A test monitor")
            .build());
    try {
      java.lang.reflect.Field field = Monitor.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(m, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return m;
  }

  private static class CapturingObserver implements StreamObserver<PlatformMessage> {
    PlatformMessage message;
    Throwable error;
    boolean completed;

    @Override
    public void onNext(PlatformMessage message) {
      this.message = message;
    }

    @Override
    public void onError(Throwable throwable) {
      this.error = throwable;
    }

    @Override
    public void onCompleted() {
      this.completed = true;
    }
  }

  private static AgentMessage registration(String token) {
    return AgentMessage.newBuilder()
        .setRegistration(RegistrationRequest.newBuilder()
            .setRegistrationToken(token)
            .build())
        .build();
  }

  private static AgentMessage authentication(String credential) {
    return AgentMessage.newBuilder()
        .setAuthentication(AuthenticationRequest.newBuilder()
            .setCredential(credential)
            .build())
        .build();
  }

  private static AgentMessage heartbeat() {
    return AgentMessage.newBuilder()
        .setHeartbeat(Heartbeat.newBuilder().build())
        .build();
  }

  private static AgentMessage observations(String batchId, List<Observation> observations) {
    return AgentMessage.newBuilder()
        .setObservations(ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addAllObservations(observations)
            .build())
        .build();
  }

  private static AgentMessage emptyMessage() {
    return AgentMessage.newBuilder().build();
  }

  private static AgentMessage descriptorApplied(boolean success, String version, String error) {
    DescriptorApplied.Builder builder = DescriptorApplied.newBuilder()
        .setSuccess(success)
        .setDescriptorVersion(version);
    if (error != null) {
      builder.setErrorMessage(error);
    }
    return AgentMessage.newBuilder().setDescriptorApplied(builder.build()).build();
  }

  private static void assertSignalsError(CapturingObserver observer, Status.Code code) {
    assertThat(observer.error).isNotNull();
    assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(code);
  }

  @Nested
  @DisplayName("connect - registration")
  class ConnectRegistration {

    @Test
    @DisplayName("Should register agent and send RegistrationAccepted with credential")
    void connect_Registration_SendsRegistrationAccepted() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();

      com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent registeredAgent =
          new com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent(id, "agent-cred");
      Monitor monitor = monitor(id);

      when(agentCredentialService.registerAgent("reg-token")).thenReturn(registeredAgent);
      when(monitorRepository.findById(id)).thenReturn(java.util.Optional.of(monitor));
      mockOpenConnection(id, responseObserver);

      StreamObserver<AgentMessage> requestObserver =
          agentGrpcService.connect(responseObserver);
      requestObserver.onNext(registration("reg-token"));

      assertThat(responseObserver.error).isNull();
      assertThat(responseObserver.message).isNotNull();
      assertThat(responseObserver.message.hasRegistrationAccepted()).isTrue();
      RegistrationAccepted accepted = responseObserver.message.getRegistrationAccepted();
      assertThat(accepted.getAgentId()).isEqualTo(id.toString());
      assertThat(accepted.getCredential()).isEqualTo("agent-cred");
      assertThat(accepted.getMonitorDescriptor().getId()).isEqualTo("desc-1");
    }

    @Test
    @DisplayName("Should signal INTERNAL when first message is neither registration nor authentication")
    void connect_FirstMessageInvalid_SignalsInternal() throws Exception {
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = agentGrpcService.connect(responseObserver);

      requestObserver.onNext(emptyMessage());

      assertSignalsError(responseObserver, Status.Code.INTERNAL);
    }

    @Test
    @DisplayName("Should signal error when monitor does not exist after registration")
    void connect_Registration_MonitorNotFound_SignalsError() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();

      com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent registeredAgent =
          new com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent(id, "agent-cred");

      when(agentCredentialService.registerAgent("reg-token")).thenReturn(registeredAgent);
      when(monitorRepository.findById(id)).thenReturn(java.util.Optional.empty());

      StreamObserver<AgentMessage> requestObserver = agentGrpcService.connect(responseObserver);
      requestObserver.onNext(registration("reg-token"));

      assertThat(responseObserver.error).isNotNull();
    }
  }

  @Nested
  @DisplayName("connect - authentication")
  class ConnectAuthentication {

    @Test
    @DisplayName("Should authenticate agent and send DescriptorUpdate")
    void connect_Authentication_SendsDescriptorUpdate() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      Monitor monitor = monitor(id);

      when(agentCredentialService.authenticate("agent-cred")).thenReturn(id);
      when(monitorRepository.findById(id)).thenReturn(java.util.Optional.of(monitor));
      mockOpenConnection(id, responseObserver);

      StreamObserver<AgentMessage> requestObserver = agentGrpcService.connect(responseObserver);
      requestObserver.onNext(authentication("agent-cred"));

      assertThat(responseObserver.error).isNull();
      assertThat(responseObserver.message).isNotNull();
      assertThat(responseObserver.message.hasDescriptorUpdate()).isTrue();
      assertThat(responseObserver.message.getDescriptorUpdate().getMonitorDescriptor().getId())
          .isEqualTo("desc-1");
    }
  }

  @Nested
  @DisplayName("connect - messages after initialization")
  class ConnectAfterInit {

    @Test
    @DisplayName("Should respond to heartbeat with HeartbeatResponse")
    void connect_Heartbeat_SendsHeartbeatResponse() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(heartbeat());

      assertThat(responseObserver.error).isNull();
      assertThat(responseObserver.message).isNotNull();
      assertThat(responseObserver.message.hasHeartbeatResponse()).isTrue();
    }

    @Test
    @DisplayName("Should save observations and send acknowledgement")
    void connect_Observations_SavesAndAcknowledges() throws Exception {
      UUID id = monitorId();
      Observation observation = Observation.newBuilder()
          .setMetric("cpu")
          .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
              .setSeconds(Instant.now().getEpochSecond())
              .build())
          .build();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(observations("batch-1", List.of(observation)));

      verify(observationService).saveBatch(eq(id), eq("batch-1"), eq(List.of(observation)));
      assertThat(responseObserver.error).isNull();
      assertThat(responseObserver.message).isNotNull();
      assertThat(responseObserver.message.hasObservationAcknowledgement()).isTrue();
      assertThat(responseObserver.message.getObservationAcknowledgement().getSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should signal INVALID_ARGUMENT when observation batch id is blank")
    void connect_Observations_BlankBatchId_SignalsError() throws Exception {
      UUID id = monitorId();
      Observation observation = Observation.newBuilder().build();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(
          AgentMessage.newBuilder().setObservations(
              ObservationBatch.newBuilder().setBatchId("   ").addObservations(observation)
          ).build());

      assertSignalsError(responseObserver, Status.Code.INTERNAL);
    }

    @Test
    @DisplayName("Should signal INVALID_ARGUMENT when observation batch is empty")
    void connect_Observations_EmptyBatch_SignalsError() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(
          AgentMessage.newBuilder().setObservations(
              ObservationBatch.newBuilder().setBatchId("batch-1")
          ).build());

      assertSignalsError(responseObserver, Status.Code.INTERNAL);
    }

    @Test
    @DisplayName("Should ignore failed descriptor applied")
    void connect_DescriptorApplied_Failure_Ignored() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(descriptorApplied(false, "1.0", "apply failed"));

      assertThat(responseObserver.error).isNull();
      verify(observationService, never()).saveBatch(any(), any(), any());
    }

    @Test
    @DisplayName("Should ignore successful descriptor applied (no persistence)")
    void connect_DescriptorApplied_Success_Ignored() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(descriptorApplied(true, "1.0", null));

      assertThat(responseObserver.error).isNull();
      verify(observationService, never()).saveBatch(any(), any(), any());
    }

    @Test
    @DisplayName("Should signal INVALID_ARGUMENT when registration sent after init")
    void connect_Registration_AfterInit_SignalsError() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onNext(registration("reg-token"));

      assertSignalsError(responseObserver, Status.Code.INTERNAL);
    }
  }

  @Nested
  @DisplayName("connect - lifecycle")
  class ConnectLifecycle {

    @Test
    @DisplayName("Should close connection on onError")
    void connect_OnError_ClosesConnection() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onError(new RuntimeException("boom"));

      verify(agentConnectionService).closeConnection(any());
    }

    @Test
    @DisplayName("Should complete session on onCompleted")
    void connect_OnCompleted_CompletesSession() throws Exception {
      UUID id = monitorId();
      CapturingObserver responseObserver = new CapturingObserver();
      StreamObserver<AgentMessage> requestObserver = initialize(id, responseObserver);

      requestObserver.onCompleted();

      verify(agentConnectionService).closeConnection(any());
    }
  }

  private StreamObserver<AgentMessage> initialize(UUID id, CapturingObserver responseObserver)
      throws Exception {
    com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent registeredAgent =
        new com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent(id, "agent-cred");
    Monitor monitor = monitor(id);

    when(agentCredentialService.registerAgent("reg-token")).thenReturn(registeredAgent);
    when(monitorRepository.findById(id)).thenReturn(java.util.Optional.of(monitor));
    mockOpenConnection(id, responseObserver);

    StreamObserver<AgentMessage> requestObserver =
        agentGrpcService.connect(responseObserver);
    requestObserver.onNext(registration("reg-token"));
    return requestObserver;
  }

  private void mockOpenConnection(UUID id, CapturingObserver responseObserver) {
    when(agentConnectionService.openConnection(eq(id), any()))
        .thenAnswer((Answer<AgentConnectionService.AgentSession>) invocation -> {
          StreamObserver<PlatformMessage> obs = invocation.getArgument(1);
          return realConnectionService.openConnection(id, obs);
        });
  }
}
