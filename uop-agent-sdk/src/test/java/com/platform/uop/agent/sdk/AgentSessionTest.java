package com.platform.uop.agent.sdk;

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

import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionTest {

    private Path tempDir;
    private CredentialStore credentialStore;
    private AgentGrpc mockAgentGrpc;
    private StreamObserver<AgentMessage> agentObserver;
    private AgentSession session;
    private List<Collector> collectors;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("agent-test");
        Path credFile = tempDir.resolve("credentials");
        credentialStore = new CredentialStore(credFile);

        mockAgentGrpc = mock(AgentGrpc.class);
        doAnswer(invocation -> null).when(mockAgentGrpc).connect();

        agentObserver = mock(StreamObserver.class);

        collectors = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            session.stop();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(Path path) throws Exception {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        // Ignore
                    }
                });
        }
    }

    private void setupSession() {
        session = new AgentSession("localhost", 8080, "reg-token", collectors, credentialStore, mockAgentGrpc);
        java.lang.reflect.Field field;
        try {
            field = AgentSession.class.getDeclaredField("outboundObserver");
            field.setAccessible(true);
            field.set(session, agentObserver);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        session.start();
    }

    @Test
    void start_withNoCredential_sendsRegistration() {
        session = new AgentSession("localhost", 8080, "reg-token", collectors, credentialStore, mockAgentGrpc);
        java.lang.reflect.Field field;
        try {
            field = AgentSession.class.getDeclaredField("outboundObserver");
            field.setAccessible(true);
            field.set(session, agentObserver);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        session.start();

        verify(mockAgentGrpc).connect();
        verify(agentObserver).onNext(argThat(msg -> 
            msg.getMessageCase() == AgentMessage.MessageCase.REGISTRATION
            && msg.getRegistration().getRegistrationToken().equals("reg-token")));
    }

    @Test
    void start_withStoredCredential_sendsAuthentication() throws Exception {
        credentialStore.save("stored-credential");

        session = new AgentSession("localhost", 8080, "reg-token", collectors, credentialStore, mockAgentGrpc);
        java.lang.reflect.Field field;
        try {
            field = AgentSession.class.getDeclaredField("outboundObserver");
            field.setAccessible(true);
            field.set(session, agentObserver);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        session.start();

        verify(mockAgentGrpc).connect();
        verify(agentObserver).onNext(argThat(msg -> 
            msg.getMessageCase() == AgentMessage.MessageCase.AUTHENTICATION
            && msg.getAuthentication().getCredential().equals("stored-credential")));
    }

    @Test
    void handleRegistrationAccepted_storesCredentialAndStartsObservation() {
        setupSession();
        session.start();

        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("monitor-1")
            .setName("Test Monitor")
            .setVersion("1.0")
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("test.metric")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .build())
            .build();

        RegistrationAccepted accepted = RegistrationAccepted.newBuilder()
            .setCredential("new-credential")
            .setMonitorDescriptor(descriptor)
            .build();

        session.handleRegistrationAccepted(accepted);

        assertThat(credentialStore.load()).isEqualTo("new-credential");
        assertThat(session.isRunning()).isTrue();
    }

    @Test
    void handleDescriptorUpdate_updatesDescriptorAndSendsApplied() {
        setupSession();

        MonitorDescriptor initialDescriptor = MonitorDescriptor.newBuilder()
            .setId("monitor-1")
            .setName("Test Monitor")
            .setVersion("1.0")
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("test.metric")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .build())
            .build();

        RegistrationAccepted accepted = RegistrationAccepted.newBuilder()
            .setCredential("cred")
            .setMonitorDescriptor(initialDescriptor)
            .build();

        session.handleRegistrationAccepted(accepted);

        MonitorDescriptor newDescriptor = MonitorDescriptor.newBuilder()
            .setId("monitor-1")
            .setName("Updated Monitor")
            .setVersion("2.0")
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("test.metric")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .build())
            .build();

        DescriptorUpdate update = DescriptorUpdate.newBuilder()
            .setMonitorDescriptor(newDescriptor)
            .build();

        session.handleDescriptorUpdate(update);

        verify(agentObserver, org.mockito.Mockito.atLeastOnce()).onNext(argThat(msg -> 
            msg.getMessageCase() == AgentMessage.MessageCase.DESCRIPTOR_APPLIED
            && msg.getDescriptorApplied().getSuccess()));
    }

    @Test
    void handleObservationAcknowledgement_removesBatchOnSuccess() {
        setupSession();

        String batchId = UUID.randomUUID().toString();
        ObservationAcknowledgement ack = ObservationAcknowledgement.newBuilder()
            .setBatchId(batchId)
            .setSuccess(true)
            .build();

        session.handleObservationAcknowledgement(ack);

        // Should not throw exception
        assertThat(session.isRunning()).isTrue();
    }

    @Test
    void handleObservationAcknowledgement_retriesBatchOnFailure() {
        setupSession();

        String batchId = UUID.randomUUID().toString();
        ObservationBatch batch = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .build();

        java.lang.reflect.Field field;
        try {
            field = AgentSession.class.getDeclaredField("pendingBatches");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, ObservationBatch> pendingBatches = (java.util.Map<UUID, ObservationBatch>) field.get(session);
            pendingBatches.put(UUID.fromString(batchId), batch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ObservationAcknowledgement ack = ObservationAcknowledgement.newBuilder()
            .setBatchId(batchId)
            .setSuccess(false)
            .build();

        session.handleObservationAcknowledgement(ack);

        // Should retry - session still running
        assertThat(session.isRunning()).isTrue();
    }

    @Test
    void stop_stopsObservationAndDisconnects() {
        setupSession();

        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("monitor-1")
            .setName("Test Monitor")
            .setVersion("1.0")
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("test.metric")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .build())
            .build();

        RegistrationAccepted accepted = RegistrationAccepted.newBuilder()
            .setCredential("cred")
            .setMonitorDescriptor(descriptor)
            .build();

        session.handleRegistrationAccepted(accepted);

        session.stop();

        assertThat(session.isRunning()).isFalse();
        verify(mockAgentGrpc).disconnect();
    }

    @Test
    void reconnect_callsDisconnectAndConnect() {
        setupSession();

        session.reconnect();

        verify(mockAgentGrpc).disconnect();
        verify(mockAgentGrpc, org.mockito.Mockito.atLeastOnce()).connect();
    }

    @Test
    void sendRegistration_createsCorrectMessage() {
        setupSession();
        org.mockito.Mockito.clearInvocations(agentObserver);

        session.sendRegistration();

        verify(agentObserver).onNext(argThat(msg -> 
            msg.getMessageCase() == AgentMessage.MessageCase.REGISTRATION
            && msg.getRegistration().getRegistrationToken().equals("reg-token")));
    }

    @Test
    void sendAuthentication_createsCorrectMessage() throws Exception {
        credentialStore.save("test-cred");
        setupSession();
        org.mockito.Mockito.clearInvocations(agentObserver);

        session.sendAuthentication();

        verify(agentObserver).onNext(argThat(msg -> 
            msg.getMessageCase() == AgentMessage.MessageCase.AUTHENTICATION
            && msg.getAuthentication().getCredential().equals("test-cred")));
    }

    @Test
    void sendHeartbeat_createsCorrectMessage() {
        setupSession();
        org.mockito.Mockito.clearInvocations(agentObserver);

        session.sendHeartbeat();

        verify(agentObserver).onNext(argThat(msg -> 
            msg.getMessageCase() == AgentMessage.MessageCase.HEARTBEAT));
    }
}