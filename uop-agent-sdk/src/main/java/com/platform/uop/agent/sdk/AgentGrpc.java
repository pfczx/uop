package com.platform.uop.agent.sdk;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.PlatformMessage;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class AgentGrpc implements StreamObserver<PlatformMessage> {

  private static final Logger logger = LoggerFactory.getLogger(AgentGrpc.class);

  // for tests only
  @VisibleForTesting
  private AgentSession agentSession;

  private final String platformHost;
  private final int platformPort;

  private ManagedChannel channel;
  private AgentServiceGrpc.AgentServiceStub stub;

  public AgentGrpc(
      AgentSession agentSession,
      String platformHost,
      int platformPort) {
    this(agentSession, platformHost, platformPort, null);
  }

  AgentGrpc(
      AgentSession agentSession,
      String platformHost,
      int platformPort,
      ManagedChannel channel) {
    this.agentSession = agentSession;
    this.platformHost = platformHost;
    this.platformPort = platformPort;
    this.channel = channel;
  }

  // for tests only
  @VisibleForTesting
  void setAgentSession(AgentSession agentSession) {
    this.agentSession = agentSession;
  }

  @Override
  public void onNext(PlatformMessage message) {
    try {
      handleMessage(message);
    } catch (StatusRuntimeException exception) {
      fail(exception);
    } catch (Exception exception) {
      logger.error("Error handling platform message", exception);

      fail(Status.INTERNAL
          .withDescription("Failed to process platform message")
          .withCause(exception)
          .asRuntimeException());
    }
  }

  private void handleMessage(PlatformMessage message) {
    switch (message.getMessageCase()) {
      case REGISTRATION_ACCEPTED -> agentSession.handleRegistrationAccepted(
          message.getRegistrationAccepted());

      case DESCRIPTOR_UPDATE -> agentSession.handleDescriptorUpdate(
          message.getDescriptorUpdate());

      case HEARTBEAT_RESPONSE -> agentSession.handleHeartbeatResponse(
          message.getHeartbeatResponse());

      case OBSERVATION_ACKNOWLEDGEMENT -> agentSession.handleObservationAcknowledgement(
          message.getObservationAcknowledgement());

      case MESSAGE_NOT_SET -> throw Status.INVALID_ARGUMENT
          .withDescription("Platform message must contain a payload")
          .asRuntimeException();
    }

  };

  public void connect() {
    if (channel == null) {
      this.channel = NettyChannelBuilder
          .forAddress(new java.net.InetSocketAddress(platformHost, platformPort))
          .usePlaintext()
          .build();
    }
    this.stub = AgentServiceGrpc.newStub(this.channel);

    StreamObserver<AgentMessage> outboundObserver = this.stub.connect(this);

    this.agentSession.setOutboundObserver(outboundObserver);

    logger.info("AgentGrpc - Connected to {}:{} (plaintext)", platformHost, platformPort);
  };

  public void disconnect() {
    if (channel == null) {
      return;
    }

    channel.shutdown();

    try {
      if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.warn("AgentGrpc - Forcing channel shutdown");
        channel.shutdownNow();
      }
    } catch (InterruptedException exception) {
      channel.shutdownNow();
      Thread.currentThread().interrupt();
    }

    logger.info("AgentGrpc - Disconnected");
  }

  private void fail(Throwable error) {
    if (agentSession.isRunning()) {
      agentSession.handleConnectionError(error);
    }
  };

  @Override
  public void onError(Throwable cause) {
    if (agentSession.isRunning()) {
      agentSession.handleConnectionError(cause);
    }

    logger.info("AgentGrpc - Connection closed with error");

  };

  @Override
  public void onCompleted() {
    if (agentSession.isRunning()) {
      agentSession.handleConnectionClosed();
    }

    logger.info("AgentGrpc - Connection completed");

  };
}
