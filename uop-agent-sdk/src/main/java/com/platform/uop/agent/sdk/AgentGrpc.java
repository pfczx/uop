package com.platform.uop.agent.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.HeartbeatResponse;
import com.platform.uop.proto.v1.PlatformMessage;
import com.platform.uop.proto.v1.RegistrationAccepted;
import com.platform.uop.proto.v1.AgentServiceGrpc.AgentServiceImplBase;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class AgentGrpc implements StreamObserver<PlatformMessage> {

  private static final Logger logger = LoggerFactory.getLogger(AgentGrpc.class);

  private final AgentSession agentSession;

  private ManagedChannel channel;
  private AgentServiceGrpc.AgentServiceStub stub;

  private boolean initialized;

  public AgentGrpc(AgentSession agentSession) {
    this.agentSession = agentSession;
  };

  @Override
  public void onNext(PlatformMessage message);

  // Inbound message handling

  private void handleMessage(PlatformMessage message);

  // Transport lifecycle

  public void connect();

  public void disconnect();

  // Error handling

  private void fail(Throwable error);

  @Override
  public void onError(Throwable cause);

  @Override
  public void onCompleted();
}
