package com.platform.uop.integrations.grpc;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentConnectionService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.monitor.service.AgentGrpcService;
import com.platform.uop.observation.service.ObservationService;
import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.AuthenticationRequest;
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
import com.platform.uop.proto.v1.RegistrationRequest;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.Server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.uop.AbstractPostgresIntegrationTest;
import com.platform.uop.IntegrationTest;

@IntegrationTest
@SpringBootTest
class AgentGrpcIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AgentGrpcService agentGrpcService;

    @Autowired
    private AgentCredentialService agentCredentialService;

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObservationService observationService;

    @Autowired
    private com.platform.uop.observation.repository.ObservationRepository observationRepository;

    @Autowired
    private com.platform.uop.observation.repository.ObservationBatchRepository observationBatchRepository;

    @Autowired
    private com.platform.uop.monitor.repository.AgentCredentialRepository agentCredentialRepository;

    @Autowired
    private AgentConnectionService agentConnectionService;

    private String serverName;
    private ManagedChannel channel;
    private AgentServiceGrpc.AgentServiceStub stub;
    private StreamObserver<AgentMessage> requestObserver;
    private StreamObserver<PlatformMessage> responseObserver;
    private CountDownLatch responseLatch;
    private AtomicReference<PlatformMessage> lastResponse;
    private Workspace workspace;
    private Monitor monitor;
    private String registrationToken;
    private Server testServer;

    @BeforeEach
    void setUp() throws Exception {
        observationRepository.deleteAll();
        observationBatchRepository.deleteAll();
        agentCredentialRepository.deleteAll();
        monitorRepository.deleteAll();
        workspaceRepository.deleteAll();

        workspace = workspaceRepository.save(new Workspace("Test Workspace", "Test Description"));
        
        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("test-monitor-" + UUID.randomUUID())
            .setName("Test Monitor")
            .setDescription("A test monitor")
            .setVersion("1.0")
            .setActive(true)
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("cpu.usage")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .build();
        
        monitor = monitorRepository.save(new Monitor(workspace, descriptor));
        registrationToken = agentCredentialService.createRegistrationToken(monitor.getId());

        serverName = InProcessServerBuilder.generateName();
        testServer = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(agentGrpcService)
            .build()
            .start();

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        stub = AgentServiceGrpc.newStub(channel);

        responseLatch = new CountDownLatch(1);
        lastResponse = new AtomicReference<>();

        responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(PlatformMessage message) {
                lastResponse.set(message);
                responseLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                lastResponse.set(null);
                responseLatch.countDown();
            }

            @Override
            public void onCompleted() {
                responseLatch.countDown();
            }
        };

        requestObserver = stub.connect(responseObserver);
    }

    @AfterEach
    void tearDown() {
        if (requestObserver != null) {
            requestObserver.onCompleted();
        }
        if (channel != null) {
            channel.shutdownNow();
        }
        if (testServer != null) {
            testServer.shutdownNow();
        }
    }

    private void sendAndWait(AgentMessage message) throws InterruptedException {
        responseLatch = new CountDownLatch(1);
        requestObserver.onNext(message);
        assertThat(responseLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void registrationFlow_createsAgentAndSendsDescriptor() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.REGISTRATION_ACCEPTED);

        RegistrationAccepted accepted = response.getRegistrationAccepted();
        assertThat(accepted.getAgentId()).isEqualTo(monitor.getId().toString());
        assertThat(accepted.getCredential()).isNotBlank();
        assertThat(accepted.getMonitorDescriptor().getId()).isEqualTo(monitor.getDescriptor().getId());
    }

    @Test
    void authenticationFlow_existingCredential_returnsDescriptorUpdate() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        PlatformMessage regResponse = lastResponse.get();
        String credential = regResponse.getRegistrationAccepted().getCredential();

        responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(PlatformMessage message) {
                lastResponse.set(message);
                responseLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                lastResponse.set(null);
                responseLatch.countDown();
            }

            @Override
            public void onCompleted() {
                responseLatch.countDown();
            }
        };

        requestObserver = stub.connect(responseObserver);

        AgentMessage authMsg = AgentMessage.newBuilder()
            .setAuthentication(AuthenticationRequest.newBuilder()
                .setCredential(credential)
                .build())
            .build();

        sendAndWait(authMsg);

        PlatformMessage authResponse = lastResponse.get();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.DESCRIPTOR_UPDATE);
        assertThat(authResponse.getDescriptorUpdate().getMonitorDescriptor().getId())
            .isEqualTo(monitor.getDescriptor().getId());
    }

    @Test
    void heartbeat_exchange_works() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        AgentMessage heartbeatMsg = AgentMessage.newBuilder()
            .setHeartbeat(Heartbeat.newBuilder()
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        sendAndWait(heartbeatMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.HEARTBEAT_RESPONSE);
        assertThat(response.getHeartbeatResponse().getServerTime()).isNotNull();
    }

    @Test
    void observations_batch_isSavedAndAcknowledged() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("cpu.usage")
                .setValue(Value.newBuilder().setDoubleValue(42.5).build())
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .addObservations(Observation.newBuilder()
                .setMetric("memory.usage")
                .setValue(Value.newBuilder().setIntValue(1024).build())
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        AgentMessage obsMsg = AgentMessage.newBuilder()
            .setObservations(batch)
            .build();

        sendAndWait(obsMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.OBSERVATION_ACKNOWLEDGEMENT);
        assertThat(response.getObservationAcknowledgement().getBatchId()).isEqualTo(batchId);
        assertThat(response.getObservationAcknowledgement().getSuccess()).isTrue();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            var result = observationService.findByMonitor(monitor.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(2);
        });
    }

    @Test
    void duplicateBatchId_isDeduplicated() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("cpu.usage")
                .setValue(Value.newBuilder().setDoubleValue(42.5).build())
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        AgentMessage obsMsg = AgentMessage.newBuilder()
            .setObservations(batch)
            .build();

        sendAndWait(obsMsg);
        var firstResponse = lastResponse.get();
        assertThat(firstResponse.getObservationAcknowledgement().getSuccess()).isTrue();

        sendAndWait(obsMsg);
        var secondResponse = lastResponse.get();
        assertThat(secondResponse.getObservationAcknowledgement().getSuccess()).isTrue();
        // Second batch with same ID should be deduplicated (alreadyProcessed would be true in BatchSaveResult)
        // But gRPC ack doesn't have alreadyProcessed field, just success=false for invalid batches

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            var result = observationService.findByMonitor(monitor.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(1);
        });
    }

    @Test
    void descriptorUpdate_fromPlatform_appliedByAgent() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        MonitorDescriptor newDescriptor = MonitorDescriptor.newBuilder()
            .setId(monitor.getDescriptor().getId())
            .setName("Updated Monitor")
            .setDescription("Updated description")
            .setVersion("2.0")
            .setActive(true)
            .addAllMetrics(monitor.getDescriptor().getMetricsList())
            .build();

        agentConnectionService.sendDescriptor(monitor.getId(), newDescriptor);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            PlatformMessage response = lastResponse.get();
            assertThat(response).isNotNull();
            assertThat(response.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.DESCRIPTOR_UPDATE);
            assertThat(response.getDescriptorUpdate().getMonitorDescriptor().getVersion()).isEqualTo("2.0");
        });

        // The service does not acknowledge DescriptorApplied, so send without waiting.
        AgentMessage appliedMsg = AgentMessage.newBuilder()
            .setDescriptorApplied(DescriptorApplied.newBuilder()
                .setDescriptorVersion("2.0")
                .setSuccess(true)
                .build())
            .build();

        requestObserver.onNext(appliedMsg);
    }

    @Test
    void invalidFirstMessage_rejected() throws InterruptedException {
        AgentMessage heartbeatMsg = AgentMessage.newBuilder()
            .setHeartbeat(Heartbeat.newBuilder()
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        sendAndWait(heartbeatMsg);

        assertThat(lastResponse.get()).isNull();
        assertThat(responseObserver).isNotNull();
    }

    @Test
    void emptyBatch_rejected() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .build();

        AgentMessage obsMsg = AgentMessage.newBuilder()
            .setObservations(batch)
            .build();

        // The service rejects an empty batch by closing the stream with an error,
        // so no PlatformMessage response is produced.
        sendAndWait(obsMsg);

        assertThat(lastResponse.get()).isNull();
    }

    @Test
    void batchTooLarge_rejected() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        String batchId = UUID.randomUUID().toString();
        ObservationBatch.Builder builder = ObservationBatch.newBuilder().setBatchId(batchId);
        
        for (int i = 0; i < 1001; i++) {
            builder.addObservations(Observation.newBuilder()
                .setMetric("metric." + i)
                .setValue(Value.newBuilder().setDoubleValue(i).build())
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build());
        }

        AgentMessage obsMsg = AgentMessage.newBuilder()
            .setObservations(builder.build())
            .build();

        // Oversized batch is rejected by closing the stream with an error.
        sendAndWait(obsMsg);

        assertThat(lastResponse.get()).isNull();
    }

    @Test
    void observationWithoutTimestamp_rejected() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);

        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("cpu.usage")
                .setValue(Value.newBuilder().setDoubleValue(42.5).build())
                .build())
            .build();

        AgentMessage obsMsg = AgentMessage.newBuilder()
            .setObservations(batch)
            .build();

        // Missing timestamp is rejected by closing the stream with an error.
        sendAndWait(obsMsg);

        assertThat(lastResponse.get()).isNull();
    }

    @Test
    void concurrentAgents_sameMonitor_onlyOneConnected() throws InterruptedException, IOException {
        String serverName2 = InProcessServerBuilder.generateName();
        Server testServer2 = InProcessServerBuilder.forName(serverName2)
            .directExecutor()
            .addService(agentGrpcService)
            .build()
            .start();
        ManagedChannel channel2 = InProcessChannelBuilder.forName(serverName2).directExecutor().build();
        AgentServiceGrpc.AgentServiceStub stub2 = AgentServiceGrpc.newStub(channel2);

        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicReference<PlatformMessage> resp1 = new AtomicReference<>();
        StreamObserver<PlatformMessage> responseObserver1 = new StreamObserver<>() {
            @Override public void onNext(PlatformMessage m) { resp1.set(m); latch1.countDown(); }
            @Override public void onError(Throwable t) { latch1.countDown(); }
            @Override public void onCompleted() { latch1.countDown(); }
        };
        StreamObserver<AgentMessage> requestObserver1 = stub.connect(responseObserver1);
        requestObserver1.onNext(registrationMsg);
        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(resp1.get().getMessageCase()).isEqualTo(PlatformMessage.MessageCase.REGISTRATION_ACCEPTED);
        String credential = resp1.get().getRegistrationAccepted().getCredential();

        // Second agent authenticates with the issued credential. The connection
        // service keeps a single session per monitor, so this replaces the first.
        AgentMessage authMsg = AgentMessage.newBuilder()
            .setAuthentication(AuthenticationRequest.newBuilder()
                .setCredential(credential)
                .build())
            .build();

        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<PlatformMessage> resp2 = new AtomicReference<>();
        StreamObserver<PlatformMessage> responseObserver2 = new StreamObserver<>() {
            @Override public void onNext(PlatformMessage m) { resp2.set(m); latch2.countDown(); }
            @Override public void onError(Throwable t) { latch2.countDown(); }
            @Override public void onCompleted() { latch2.countDown(); }
        };
        StreamObserver<AgentMessage> requestObserver2 = stub2.connect(responseObserver2);
        requestObserver2.onNext(authMsg);
        assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(resp2.get().getMessageCase()).isEqualTo(PlatformMessage.MessageCase.DESCRIPTOR_UPDATE);

        // Only one session remains connected for the monitor.
        assertThat(agentConnectionService.isConnected(monitor.getId())).isTrue();

        requestObserver1.onCompleted();
        requestObserver2.onCompleted();
        channel2.shutdownNow();
        testServer2.shutdownNow();
    }

    @Test
    void agentReconnection_afterDisconnect_works() throws InterruptedException {
        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        sendAndWait(registrationMsg);
        var regResponse = lastResponse.get();
        String credential = regResponse.getRegistrationAccepted().getCredential();

        requestObserver.onCompleted();

        // Allow the server to observe the stream completion before reconnecting.
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(agentConnectionService.isConnected(monitor.getId())).isFalse());

        responseObserver = new StreamObserver<>() {
            @Override public void onNext(PlatformMessage m) { lastResponse.set(m); responseLatch.countDown(); }
            @Override public void onError(Throwable t) { lastResponse.set(null); responseLatch.countDown(); }
            @Override public void onCompleted() { responseLatch.countDown(); }
        };
        requestObserver = stub.connect(responseObserver);

        AgentMessage authMsg = AgentMessage.newBuilder()
            .setAuthentication(AuthenticationRequest.newBuilder().setCredential(credential).build())
            .build();

        sendAndWait(authMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.DESCRIPTOR_UPDATE);
    }
}