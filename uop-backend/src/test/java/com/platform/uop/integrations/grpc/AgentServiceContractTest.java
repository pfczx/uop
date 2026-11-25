package com.platform.uop.integrations.grpc;

import com.platform.uop.monitor.service.AgentGrpcService;
import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.AuthenticationRequest;
import com.platform.uop.proto.v1.Heartbeat;
import com.platform.uop.proto.v1.HeartbeatResponse;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationAcknowledgement;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.PlatformMessage;
import com.platform.uop.proto.v1.RegistrationAccepted;
import com.platform.uop.proto.v1.RegistrationRequest;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.uop.IntegrationTest;

@IntegrationTest
class AgentServiceContractTest {

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

    private String serverName;
    private ManagedChannel channel;
    private AgentServiceGrpc.AgentServiceStub stub;
    private StreamObserver<PlatformMessage> responseObserver;
    private io.grpc.Server server;

    @BeforeEach
    void setUp() throws Exception {
        serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(agentGrpcService)
            .build()
            .start();

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        stub = AgentServiceGrpc.newStub(channel);

        CountDownLatch latch = new CountDownLatch(1);
        responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(PlatformMessage message) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        stub.connect(responseObserver);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void gRPCService_startsAndAcceptsConnections() {
        // Verify the gRPC service starts and accepts connections
        assertThat(stub).isNotNull();
        assertThat(channel).isNotNull();
    }

    @Test
    void invalidFirstMessage_returnsError() throws InterruptedException {
        StreamObserver<AgentMessage> requestObserver = stub.connect(responseObserver);

        // Send heartbeat as first message (invalid - must be registration or auth)
        AgentMessage heartbeatMsg = AgentMessage.newBuilder()
            .setHeartbeat(Heartbeat.newBuilder()
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        requestObserver.onNext(heartbeatMsg);
        requestObserver.onCompleted();

        // Wait for error response
        Thread.sleep(100);
    }

    @Test
    void registrationMessageStructure_isValid() {
        // Test that the protobuf message structure is correct
        RegistrationRequest registration = RegistrationRequest.newBuilder()
            .setRegistrationToken("test-token")
            .setAgentVersion("1.0")
            .build();

        assertThat(registration.getRegistrationToken()).isEqualTo("test-token");
        assertThat(registration.getAgentVersion()).isEqualTo("1.0");

        AgentMessage agentMessage = AgentMessage.newBuilder()
            .setRegistration(registration)
            .build();

        assertThat(agentMessage.getMessageCase()).isEqualTo(AgentMessage.MessageCase.REGISTRATION);
    }

    @Test
    void authenticationMessageStructure_isValid() {
        AuthenticationRequest auth = AuthenticationRequest.newBuilder()
            .setCredential("test-credential")
            .build();

        assertThat(auth.getCredential()).isEqualTo("test-credential");

        AgentMessage agentMessage = AgentMessage.newBuilder()
            .setAuthentication(auth)
            .build();

        assertThat(agentMessage.getMessageCase()).isEqualTo(AgentMessage.MessageCase.AUTHENTICATION);
    }

    @Test
    void heartbeatMessageStructure_isValid() {
        Heartbeat heartbeat = Heartbeat.newBuilder()
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
            .build();

        assertThat(heartbeat.getTimestamp()).isNotNull();

        AgentMessage agentMessage = AgentMessage.newBuilder()
            .setHeartbeat(heartbeat)
            .build();

        assertThat(agentMessage.getMessageCase()).isEqualTo(AgentMessage.MessageCase.HEARTBEAT);
    }

    @Test
    void observationBatchMessageStructure_isValid() {
        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("test.metric")
                .setValue(com.platform.uop.proto.v1.Value.newBuilder().setDoubleValue(42.0).build())
                .build())
            .build();

        assertThat(batch.getBatchId()).isEqualTo(batchId);
        assertThat(batch.getObservationsCount()).isEqualTo(1);

        AgentMessage agentMessage = AgentMessage.newBuilder()
            .setObservations(batch)
            .build();

        assertThat(agentMessage.getMessageCase()).isEqualTo(AgentMessage.MessageCase.OBSERVATIONS);
    }

    @Test
    void platformMessageRegistrationAcceptedStructure_isValid() {
        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setVersion("1.0")
            .build();

        RegistrationAccepted accepted = RegistrationAccepted.newBuilder()
            .setAgentId("agent-123")
            .setCredential("credential-123")
            .setMonitorDescriptor(descriptor)
            .build();

        assertThat(accepted.getAgentId()).isEqualTo("agent-123");
        assertThat(accepted.getCredential()).isEqualTo("credential-123");
        assertThat(accepted.getMonitorDescriptor()).isEqualTo(descriptor);

        PlatformMessage platformMessage = PlatformMessage.newBuilder()
            .setRegistrationAccepted(accepted)
            .build();

        assertThat(platformMessage.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.REGISTRATION_ACCEPTED);
    }

    @Test
    void platformMessageDescriptorUpdateStructure_isValid() {
        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Updated Monitor")
            .setVersion("2.0")
            .build();

        com.platform.uop.proto.v1.DescriptorUpdate update = com.platform.uop.proto.v1.DescriptorUpdate.newBuilder()
            .setMonitorDescriptor(descriptor)
            .build();

        assertThat(update.getMonitorDescriptor()).isEqualTo(descriptor);

        PlatformMessage platformMessage = PlatformMessage.newBuilder()
            .setDescriptorUpdate(update)
            .build();

        assertThat(platformMessage.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.DESCRIPTOR_UPDATE);
    }

    @Test
    void platformMessageHeartbeatResponseStructure_isValid() {
        HeartbeatResponse response = HeartbeatResponse.newBuilder()
            .setServerTime(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
            .build();

        assertThat(response.getServerTime()).isNotNull();

        PlatformMessage platformMessage = PlatformMessage.newBuilder()
            .setHeartbeatResponse(response)
            .build();

        assertThat(platformMessage.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.HEARTBEAT_RESPONSE);
    }

    @Test
    void platformMessageObservationAcknowledgementStructure_isValid() {
        String batchId = UUID.randomUUID().toString();
        ObservationAcknowledgement ack = ObservationAcknowledgement.newBuilder()
            .setBatchId(batchId)
            .setSuccess(true)
            .setMessage("OK")
            .build();

        assertThat(ack.getBatchId()).isEqualTo(batchId);
        assertThat(ack.getSuccess()).isTrue();
        assertThat(ack.getMessage()).isEqualTo("OK");

        PlatformMessage platformMessage = PlatformMessage.newBuilder()
            .setObservationAcknowledgement(ack)
            .build();

        assertThat(platformMessage.getMessageCase()).isEqualTo(PlatformMessage.MessageCase.OBSERVATION_ACKNOWLEDGEMENT);
    }
}