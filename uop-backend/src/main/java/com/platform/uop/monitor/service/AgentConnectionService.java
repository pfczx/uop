package com.platform.uop.monitor.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.platform.uop.proto.v1.DescriptorUpdate;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.PlatformMessage;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@Service
public class AgentConnectionService {

  private final ConcurrentMap<UUID, AgentSession> sessions = new ConcurrentHashMap<>();

  public AgentSession openConnection(UUID monitorId, StreamObserver<PlatformMessage> responseObserver) {

    AgentSession newSession = new AgentSession(
        monitorId,
        responseObserver);

    AgentSession previousSession = sessions.put(monitorId, newSession);

    if (previousSession != null) {
      previousSession.close(
          Status.UNAVAILABLE
              .withDescription("Agent session replaced by a new connection")
              .asRuntimeException());
    }

    return newSession;
  }

  public void closeConnection(AgentSession session) {
    sessions.remove(session.monitorId(), session);
  }

  public boolean isConnected(UUID monitorId) {
    AgentSession session = sessions.get(monitorId);
    return session != null && session.isOpen();
  }

  public boolean sendDescriptor(
      UUID monitorId,
      MonitorDescriptor descriptor) {

    AgentSession session = sessions.get(monitorId);

    return session != null && session.sendDescriptor(descriptor);
  }

  public void closeMonitorConnection(UUID monitorId) {
    AgentSession session = sessions.remove(monitorId);

    if (session != null) {
      session.close(
          Status.CANCELLED
              .withDescription("Monitor was removed")
              .asRuntimeException());
    }
  }

  public static final class AgentSession {

    private final UUID monitorId;
    private final StreamObserver<PlatformMessage> responseObserver;
    private volatile boolean open = true;

    private AgentSession(
        UUID monitorId,
        StreamObserver<PlatformMessage> responseObserver) {
      this.monitorId = monitorId;
      this.responseObserver = responseObserver;
    }

    public UUID monitorId() {
      return monitorId;
    }

    public boolean isOpen() {
      return open;
    }

    public synchronized boolean sendDescriptor(
        MonitorDescriptor descriptor) {

      if (!open) {
        return false;
      }

      try {
        responseObserver.onNext(PlatformMessage.newBuilder()
            .setDescriptorUpdate(DescriptorUpdate.newBuilder()
                .setMonitorDescriptor(descriptor)
                .build())
            .build());

        return true;
      } catch (RuntimeException exception) {
        open = false;
        return false;
      }
    }

    public synchronized void send(PlatformMessage message) {
      if (!open) {
        throw Status.UNAVAILABLE
            .withDescription("Agent connection is closed")
            .asRuntimeException();
      }

      responseObserver.onNext(message);
    }

    public synchronized void close(Throwable error) {
      if (!open) {
        return;
      }

      open = false;

      try {
        responseObserver.onError(error);
      } catch (RuntimeException ignored) {
        // The remote peer may already have disconnected.
      }
    }

    public synchronized void complete() {
      if (!open) {
        return;
      }

      open = false;

      try {
        responseObserver.onCompleted();
      } catch (RuntimeException ignored) {
        // The remote peer may already have disconnected.
      }
    }
  }
}
