package com.platform.uop.agent.sdk;

import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.AuthenticationRequest;
import com.platform.uop.proto.v1.DescriptorApplied;
import com.platform.uop.proto.v1.DescriptorUpdate;
import com.platform.uop.proto.v1.Heartbeat;
import com.platform.uop.proto.v1.HeartbeatResponse;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationAcknowledgement;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.PlatformMessage;
import com.platform.uop.proto.v1.RegistrationAccepted;
import com.platform.uop.proto.v1.RegistrationRequest;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentGrpcTest {

    private String serverName;
    private ManagedChannel channel;
    private AgentSession mockSession;
    private AgentGrpc agentGrpc;
    private StreamObserver<AgentMessage> outboundObserver;

    @BeforeEach
    void setUp() throws Exception {
        serverName = InProcessServerBuilder.generateName();
        io.grpc.Server server = InProcessServerBuilder.forName(serverName).directExecutor().build().start();
        
        mockSession = mock(AgentSession.class);
        when(mockSession.isRunning()).thenReturn(true);

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        agentGrpc = new AgentGrpc(mockSession, "localhost", 0, channel);

        outboundObserver = mock(StreamObserver.class);
        doAnswer(invocation -> {
            outboundObserver = invocation.getArgument(0);
            return null;
        }).when(mockSession).setOutboundObserver(any());
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Test
    void handleRegistrationAccepted_savesCredentialAndStartsObservation() {
        String credential = "test-credential-123";
        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setVersion("1.0")
            .build();

        RegistrationAccepted accepted = RegistrationAccepted.newBuilder()
            .setCredential(credential)
            .setMonitorDescriptor(descriptor)
            .build();

        PlatformMessage message = PlatformMessage.newBuilder()
            .setRegistrationAccepted(accepted)
            .build();

        agentGrpc.onNext(message);

        verify(mockSession).handleRegistrationAccepted(accepted);
    }

    @Test
    void handleDescriptorUpdate_updatesDescriptorAndRestartsObservation() {
        MonitorDescriptor newDescriptor = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Updated Monitor")
            .setVersion("2.0")
            .build();

        DescriptorUpdate update = DescriptorUpdate.newBuilder()
            .setMonitorDescriptor(newDescriptor)
            .build();

        PlatformMessage message = PlatformMessage.newBuilder()
            .setDescriptorUpdate(update)
            .build();

        agentGrpc.onNext(message);

        verify(mockSession).handleDescriptorUpdate(update);
    }

    @Test
    void handleHeartbeatResponse_callsSessionHandler() {
        HeartbeatResponse response = HeartbeatResponse.newBuilder()
            .setServerTime(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
            .build();

        PlatformMessage message = PlatformMessage.newBuilder()
            .setHeartbeatResponse(response)
            .build();

        agentGrpc.onNext(message);

        verify(mockSession).handleHeartbeatResponse(response);
    }

    @Test
    void handleObservationAcknowledgement_callsSessionHandler() {
        String batchId = UUID.randomUUID().toString();
        ObservationAcknowledgement ack = ObservationAcknowledgement.newBuilder()
            .setBatchId(batchId)
            .setSuccess(true)
            .setMessage("OK")
            .build();

        PlatformMessage message = PlatformMessage.newBuilder()
            .setObservationAcknowledgement(ack)
            .build();

        agentGrpc.onNext(message);

        verify(mockSession).handleObservationAcknowledgement(ack);
    }

    @Test
    void handleMessageNotSet_throwsInvalidArgument() {
        PlatformMessage message = PlatformMessage.newBuilder().build();

        agentGrpc.onNext(message);

        verify(mockSession).handleConnectionError(any(io.grpc.StatusRuntimeException.class));
    }

    @Test
    void onError_callsSessionHandler() {
        Throwable cause = new RuntimeException("Connection error");

        agentGrpc.onError(cause);

        verify(mockSession).handleConnectionError(cause);
    }

    @Test
    void onCompleted_callsSessionHandler() {
        agentGrpc.onCompleted();

        verify(mockSession).handleConnectionClosed();
    }

    @Test
    void connect_createsStubAndSetsOutboundObserver() {
        agentGrpc.connect();

        verify(mockSession).setOutboundObserver(any());
    }

    @Test
    void disconnect_shutsDownChannel() {
        agentGrpc.connect();
        agentGrpc.disconnect();

        assertThat(channel.isShutdown()).isTrue();
    }
}