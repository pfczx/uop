package com.platform.uop.agent.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.HeartbeatResponse;
import com.platform.uop.proto.v1.RegistrationAccepted;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class AgentSession {

  private static final Logger logger = LoggerFactory.getLogger(AgentSession.class);

  private final AgentGrpc agentGrpc;

  private StreamObserver<AgentMessage> outboundObserver;

  public AgentSession() {
    this.agentGrpc = new AgentGrpc(this);
  }

  // Lifecycle

  public void start();

  public void close();

  public void reconnect();

  // Connection

  public void connect();

  public void setOutboundObserver(
      StreamObserver<AgentMessage> outboundObserver);

  // Outbound communication

  public void send(AgentMessage message);

  public void sendRegistration();

  public void sendAuthentication();

  public void sendHeartbeat();

  public void sendObservations(/* ObservationBatch batch */);

  // Inbound message handling

  public void handleRegistrationAccepted(RegistrationAccepted message);

  public void handleHeartbeatResponse(HeartbeatResponse message);

  // Connection events

  public void handleConnectionError(Throwable cause);

  public void handleConnectionClosed();
}
