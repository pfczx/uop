package com.platform.uop.agent.sdk;

import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.DescriptorApplied;
import com.platform.uop.proto.v1.DescriptorUpdate;
import com.platform.uop.proto.v1.Heartbeat;
import com.platform.uop.proto.v1.HeartbeatResponse;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationAcknowledgement;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.PlatformMessage;
import com.platform.uop.proto.v1.RegistrationAccepted;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSdkMockGrpcTest {

    private static final String REGISTRATION_TOKEN = "test-token-123";

    private Server server;
    private ManagedChannel channel;
    private AgentGrpc agentGrpc;
    private AgentSession session;
    private CredentialStore credentialStore;
    private MockAgentService mockService;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.tempDir = tempDir;
        this.credentialStore = new CredentialStore(tempDir.resolve("credentials"));
        this.mockService = new MockAgentService();

        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(mockService)
            .build()
            .start();

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.stop();
        }
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private void connectSession() {
        agentGrpc = new AgentGrpc(null, "localhost", 0, channel);
        session = new AgentSession(
            "localhost", 0, REGISTRATION_TOKEN, List.of(), credentialStore, agentGrpc);
        agentGrpc.setAgentSession(session);
        session.start();
    }

    private static boolean awaitUntil(BooleanSupplier condition, long timeoutMillis)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    @Test
    void agentSession_registersAndStoresCredential() throws Exception {
        connectSession();

        assertThat(awaitUntil(() -> credentialStore.load() != null, 5000)).isTrue();
        assertThat(credentialStore.load()).startsWith("credential-");

        // Registration accepted triggers a DescriptorApplied for the initial descriptor.
        assertThat(awaitUntil(() -> "1.0".equals(mockService.appliedDescriptorVersion), 5000))
            .isTrue();
        assertThat(session.isRunning()).isTrue();
    }

    @Test
    void agentSession_handlesDescriptorUpdate() throws Exception {
        connectSession();
        assertThat(awaitUntil(() -> mockService.registered, 5000)).isTrue();

        MonitorDescriptor updated = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Updated Monitor")
            .setVersion("2.0")
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("memory.usage")
                .setValueType(ValueType.VALUE_TYPE_INT64)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .build();

        // Platform pushes a descriptor update to the agent.
        agentGrpc.onNext(PlatformMessage.newBuilder()
            .setDescriptorUpdate(DescriptorUpdate.newBuilder()
                .setMonitorDescriptor(updated)
                .build())
            .build());

        assertThat(awaitUntil(() -> "2.0".equals(mockService.appliedDescriptorVersion), 5000))
            .isTrue();
    }

    @Test
    void agentSession_sendsHeartbeat() throws Exception {
        connectSession();
        assertThat(awaitUntil(() -> mockService.registered, 5000)).isTrue();

        session.sendHeartbeat();

        assertThat(awaitUntil(() -> mockService.heartbeatReceived, 5000)).isTrue();
    }

    @Test
    void agentSession_sendsObservationsAndReceivesAck() throws Exception {
        connectSession();
        assertThat(awaitUntil(() -> mockService.registered, 5000)).isTrue();

        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("cpu.usage")
                .setValue(Value.newBuilder().setDoubleValue(42.5).build())
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        // Inject a pending batch so the sender has something to flush.
        Field field = AgentSession.class.getDeclaredField("pendingBatches");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, ObservationBatch> pendingBatches =
            (Map<UUID, ObservationBatch>) field.get(session);
        pendingBatches.put(UUID.fromString(batchId), batch);

        session.sendObservations();

        assertThat(awaitUntil(() -> batchId.equals(mockService.ackedBatchId), 5000)).isTrue();
        assertThat(mockService.lastAckSuccess).isTrue();
        // Successful ack removes the batch from the pending map.
        assertThat(awaitUntil(() -> !pendingBatches.containsKey(UUID.fromString(batchId)), 5000))
            .isTrue();
    }

    @Test
    void credentialStore_saveAndLoad() {
        String credential = "test-credential-" + UUID.randomUUID();
        credentialStore.save(credential);

        assertThat(credentialStore.load()).isEqualTo(credential);
    }

    @Test
    void credentialStore_loadNonExistent_returnsNull() {
        CredentialStore store = new CredentialStore(tempDir.resolve("non-existent"));

        assertThat(store.load()).isNull();
    }

    @Test
    void agentSdk_builderValidation() {
        // Invalid port (too low)
        assertThatThrownBy(() -> AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(0)
            .registrationToken("token")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port must be between 1 and 65535");

        // Invalid port (too high)
        assertThatThrownBy(() -> AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(70000)
            .registrationToken("token")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port must be between 1 and 65535");

        // Blank host
        assertThatThrownBy(() -> AgentSdk.builder()
            .platformHost("  ")
            .platformPort(9090)
            .registrationToken("token")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Platform host must not be blank");
    }

    // Simple mock gRPC service that records what the agent sends back.
    static class MockAgentService extends AgentServiceGrpc.AgentServiceImplBase {

        private volatile boolean registered;
        private volatile boolean heartbeatReceived;
        private volatile String appliedDescriptorVersion;
        private volatile String ackedBatchId;
        private volatile boolean lastAckSuccess;

        @Override
        public StreamObserver<AgentMessage> connect(StreamObserver<PlatformMessage> responseObserver) {
            return new StreamObserver<>() {
                private boolean initialized = false;

                @Override
                public void onNext(AgentMessage message) {
                    if (!initialized) {
                        handleInitialization(message, responseObserver);
                        return;
                    }

                    switch (message.getMessageCase()) {
                        case HEARTBEAT -> {
                            heartbeatReceived = true;
                            responseObserver.onNext(PlatformMessage.newBuilder()
                                .setHeartbeatResponse(HeartbeatResponse.newBuilder()
                                    .setServerTime(com.google.protobuf.util.Timestamps
                                        .fromMillis(System.currentTimeMillis()))
                                    .build())
                                .build());
                        }
                        case OBSERVATIONS -> {
                            ObservationBatch batch = message.getObservations();
                            ackedBatchId = batch.getBatchId();
                            lastAckSuccess = true;
                            responseObserver.onNext(PlatformMessage.newBuilder()
                                .setObservationAcknowledgement(ObservationAcknowledgement.newBuilder()
                                    .setBatchId(batch.getBatchId())
                                    .setSuccess(true)
                                    .setMessage("OK")
                                    .build())
                                .build());
                        }
                        case DESCRIPTOR_APPLIED -> {
                            DescriptorApplied applied = message.getDescriptorApplied();
                            appliedDescriptorVersion = applied.getDescriptorVersion();
                        }
                        default -> {
                            // Ignore
                        }
                    }
                }

                private void handleInitialization(
                    AgentMessage message, StreamObserver<PlatformMessage> responseObserver) {
                    switch (message.getMessageCase()) {
                        case REGISTRATION -> {
                            String token = message.getRegistration().getRegistrationToken();
                            if (REGISTRATION_TOKEN.equals(token)) {
                                String newCredential = "credential-" + UUID.randomUUID();
                                responseObserver.onNext(PlatformMessage.newBuilder()
                                    .setRegistrationAccepted(RegistrationAccepted.newBuilder()
                                        .setAgentId("test-monitor-id")
                                        .setCredential(newCredential)
                                        .setMonitorDescriptor(MonitorDescriptor.newBuilder()
                                            .setId("test-monitor")
                                            .setName("Test")
                                            .setVersion("1.0")
                                            .build())
                                        .build())
                                    .build());
                                initialized = true;
                                registered = true;
                            } else {
                                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                                    .withDescription("Invalid token").asRuntimeException());
                            }
                        }
                        case AUTHENTICATION -> {
                            String cred = message.getAuthentication().getCredential();
                            if (cred != null && cred.startsWith("credential-")) {
                                responseObserver.onNext(PlatformMessage.newBuilder()
                                    .setDescriptorUpdate(DescriptorUpdate.newBuilder()
                                        .setMonitorDescriptor(MonitorDescriptor.newBuilder()
                                            .setId("test-monitor")
                                            .setName("Test")
                                            .setVersion("1.0")
                                            .build())
                                        .build())
                                    .build());
                                initialized = true;
                                registered = true;
                            } else {
                                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                                    .withDescription("Invalid credential").asRuntimeException());
                            }
                        }
                        default -> responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                            .withDescription("First message must be registration or authentication")
                            .asRuntimeException());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // Connection error
                }

                @Override
                public void onCompleted() {
                    // Connection closed
                }
            };
        }
    }
}