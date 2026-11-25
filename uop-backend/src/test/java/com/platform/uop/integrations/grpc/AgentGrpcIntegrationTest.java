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
import io.grpc.testing.GrpcCleanupRule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.uop.IntegrationTest;

@IntegrationTest
@SpringBootTest
class AgentGrpcIntegrationTest {

    @RegisterExtension
    static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:17"))
        .withDatabaseName("uop")
        .withUsername("postgres")
        .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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

    @BeforeEach
    void setUp() throws Exception {
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
        grpcCleanup.register(InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(agentGrpcService)
            .build()
            .start());

        channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
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
                .setValue(Value.newBuilder().setInt64Value(1024).build())
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
        assertThat(secondResponse.getObservationAcknowledgement().getAlreadyProcessed()).isTrue();

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
            if (response != null && response.getMessageCase() == PlatformMessage.MessageCase.DESCRIPTOR_UPDATE) {
                assertThat(response.getDescriptorUpdate().getMonitorDescriptor().getVersion()).isEqualTo("2.0");
            }
        });

        AgentMessage appliedMsg = AgentMessage.newBuilder()
            .setDescriptorApplied(DescriptorApplied.newBuilder()
                .setDescriptorVersion("2.0")
                .setSuccess(true)
                .build())
            .build();

        sendAndWait(appliedMsg);
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

        sendAndWait(obsMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.OBSERVATION_ACKNOWLEDGEMENT);
        assertThat(response.getObservationAcknowledgement().getSuccess()).isFalse();
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

        sendAndWait(obsMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getObservationAcknowledgement().getSuccess()).isFalse();
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

        sendAndWait(obsMsg);

        PlatformMessage response = lastResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.getObservationAcknowledgement().getSuccess()).isFalse();
    }

    @Test
    void concurrentAgents_sameMonitor_onlyOneConnected() throws InterruptedException {
        String serverName2 = InProcessServerBuilder.generateName();
        ManagedChannel channel2 = grpcCleanup.register(InProcessChannelBuilder.forName(serverName2).directExecutor().build());
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

        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<PlatformMessage> resp2 = new AtomicReference<>();
        StreamObserver<PlatformMessage> responseObserver2 = new StreamObserver<>() {
            @Override public void onNext(PlatformMessage m) { resp2.set(m); latch2.countDown(); }
            @Override public void onError(Throwable t) { latch2.countDown(); }
            @Override public void onCompleted() { latch2.countDown(); }
        };
        StreamObserver<AgentMessage> requestObserver2 = stub2.connect(responseObserver2);
        requestObserver2.onNext(registrationMsg);
        assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();
        
        assertThat(resp2.get().getMessageCase()).isEqualTo(PlatformMessage.MessageCase.REGISTRATION_ACCEPTED);
        
        assertThat(agentConnectionService.isConnected(monitor.getId())).isTrue();
        
        requestObserver1.onCompleted();
        requestObserver2.onCompleted();
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
        
        Thread.sleep(100);

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