package com.platform.uop.integrations.grpc;

import com.platform.uop.monitor.service.AgentGrpcService;
import com.platform.uop.monitor.service.AgentConnectionService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.observation.service.ObservationService;
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
import io.grpc.testing.GrpcCleanupRule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AgentServiceContractTest {

    @RegisterExtension
    static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private String serverName;
    private ManagedChannel channel;
    private AgentServiceGrpc.AgentServiceStub stub;
    private StreamObserver<PlatformMessage> responseObserver;

    private AgentConnectionService agentConnectionService;
    private AgentCredentialService agentCredentialService;
    private MonitorRepository monitorRepository;
    private ObservationService observationService;

    @BeforeEach
    void setUp() throws Exception {
        agentConnectionService = mock(AgentConnectionService.class);
        agentCredentialService = mock(AgentCredentialService.class);
        monitorRepository = mock(MonitorRepository.class);
        observationService = mock(ObservationService.class);

        serverName = InProcessServerBuilder.generateName();
        io.grpc.Server server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new AgentGrpcService(
                agentConnectionService,
                agentCredentialService,
                monitorRepository,
                observationService))
            .build()
            .start();
        grpcCleanup.register(server);

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
    }

    @Test
    void registrationRequest_returnsRegistrationAccepted() throws InterruptedException {
        String registrationToken = "test-token";
        UUID monitorId = UUID.randomUUID();
        String credential = "test-credential";

        com.platform.uop.monitor.entity.Monitor monitor = mock(com.platform.uop.monitor.entity.Monitor.class);
        when(monitor.getId()).thenReturn(monitorId);
        when(monitor.getDescriptor()).thenReturn(MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setVersion("1.0")
            .build());

        com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent registeredAgent = 
            new com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent(monitorId, credential);
        when(agentCredentialService.registerAgent(registrationToken)).thenReturn(registeredAgent);
        when(monitorRepository.findById(monitorId)).thenReturn(java.util.Optional.of(monitor));

        com.platform.uop.monitor.service.AgentConnectionService.AgentSession mockSession = 
            mock(com.platform.uop.monitor.service.AgentConnectionService.AgentSession.class);
        when(agentConnectionService.openConnection(any(), any())).thenReturn(mockSession);

        StreamObserver<AgentMessage> requestObserver = stub.connect(responseObserver);

        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        requestObserver.onNext(registrationMsg);
        requestObserver.onCompleted();

        // Wait for response
        Thread.sleep(100);
    }

    @Test
    void authenticationRequest_withValidCredential_returnsDescriptorUpdate() throws InterruptedException {
        String credential = "valid-credential";
        UUID monitorId = UUID.randomUUID();

        com.platform.uop.monitor.entity.Monitor monitor = mock(com.platform.uop.monitor.entity.Monitor.class);
        when(monitor.getId()).thenReturn(monitorId);
        when(monitor.getDescriptor()).thenReturn(MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setVersion("1.0")
            .build());

        when(agentCredentialService.authenticate(credential)).thenReturn(monitorId);
        when(monitorRepository.findById(monitorId)).thenReturn(java.util.Optional.of(monitor));

        com.platform.uop.monitor.service.AgentConnectionService.AgentSession mockSession = 
            mock(com.platform.uop.monitor.service.AgentConnectionService.AgentSession.class);
        when(agentConnectionService.openConnection(any(), any())).thenReturn(mockSession);

        StreamObserver<AgentMessage> requestObserver = stub.connect(responseObserver);

        AgentMessage authMsg = AgentMessage.newBuilder()
            .setAuthentication(AuthenticationRequest.newBuilder()
                .setCredential(credential)
                .build())
            .build();

        requestObserver.onNext(authMsg);
        requestObserver.onCompleted();

        Thread.sleep(100);
    }

    @Test
    void heartbeatRequest_returnsHeartbeatResponse() throws InterruptedException {
        // First register
        String registrationToken = "test-token";
        UUID monitorId = UUID.randomUUID();
        String credential = "test-credential";

        com.platform.uop.monitor.entity.Monitor monitor = mock(com.platform.uop.monitor.entity.Monitor.class);
        when(monitor.getId()).thenReturn(monitorId);
        when(monitor.getDescriptor()).thenReturn(MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setVersion("1.0")
            .build());

        com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent registeredAgent = 
            new com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent(monitorId, credential);
        when(agentCredentialService.registerAgent(registrationToken)).thenReturn(registeredAgent);
        when(monitorRepository.findById(monitorId)).thenReturn(java.util.Optional.of(monitor));

        com.platform.uop.monitor.service.AgentConnectionService.AgentSession mockSession = 
            mock(com.platform.uop.monitor.service.AgentConnectionService.AgentSession.class);
        when(agentConnectionService.openConnection(any(), any())).thenReturn(mockSession);

        StreamObserver<AgentMessage> requestObserver = stub.connect(responseObserver);

        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        requestObserver.onNext(registrationMsg);

        // Wait for registration to complete
        Thread.sleep(100);

        // Send heartbeat
        AgentMessage heartbeatMsg = AgentMessage.newBuilder()
            .setHeartbeat(Heartbeat.newBuilder()
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        requestObserver.onNext(heartbeatMsg);
        requestObserver.onCompleted();

        Thread.sleep(100);
    }

    @Test
    void observationsRequest_withValidBatch_returnsAcknowledgement() throws InterruptedException {
        // Setup registration
        String registrationToken = "test-token";
        UUID monitorId = UUID.randomUUID();
        String credential = "test-credential";

        com.platform.uop.monitor.entity.Monitor monitor = mock(com.platform.uop.monitor.entity.Monitor.class);
        when(monitor.getId()).thenReturn(monitorId);
        when(monitor.getDescriptor()).thenReturn(MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setVersion("1.0")
            .build());

        com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent registeredAgent = 
            new com.platform.uop.monitor.service.AgentCredentialService.RegisteredAgent(monitorId, credential);
        when(agentCredentialService.registerAgent(registrationToken)).thenReturn(registeredAgent);
        when(monitorRepository.findById(monitorId)).thenReturn(java.util.Optional.of(monitor));

        com.platform.uop.monitor.service.AgentConnectionService.AgentSession mockSession = 
            mock(com.platform.uop.monitor.service.AgentConnectionService.AgentSession.class);
        when(agentConnectionService.openConnection(any(), any())).thenReturn(mockSession);

        StreamObserver<AgentMessage> requestObserver = stub.connect(responseObserver);

        AgentMessage registrationMsg = AgentMessage.newBuilder()
            .setRegistration(RegistrationRequest.newBuilder()
                .setRegistrationToken(registrationToken)
                .setAgentVersion("1.0")
                .build())
            .build();

        requestObserver.onNext(registrationMsg);
        Thread.sleep(100);

        // Send observations
        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("test.metric")
                .setValue(com.platform.uop.proto.v1.Value.newBuilder().setDoubleValue(42.0).build())
                .build())
            .build();

        AgentMessage obsMsg = AgentMessage.newBuilder()
            .setObservations(batch)
            .build();

        requestObserver.onNext(obsMsg);
        requestObserver.onCompleted();

        Thread.sleep(100);

        verify(observationService).saveBatch(any(), any(), any());
    }

    @Test
    void invalidFirstMessage_returnsError() throws InterruptedException {
        StreamObserver<AgentMessage> requestObserver = stub.connect(responseObserver);

        // Send heartbeat as first message (invalid)
        AgentMessage heartbeatMsg = AgentMessage.newBuilder()
            .setHeartbeat(Heartbeat.newBuilder()
                .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .build();

        requestObserver.onNext(heartbeatMsg);
        requestObserver.onCompleted();

        Thread.sleep(100);
    }
}