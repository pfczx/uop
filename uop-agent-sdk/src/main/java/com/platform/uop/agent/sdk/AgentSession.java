package com.platform.uop.agent.sdk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.util.Timestamps;
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
import com.platform.uop.proto.v1.RegistrationAccepted;
import com.platform.uop.proto.v1.RegistrationRequest;

import io.grpc.stub.StreamObserver;

public class AgentSession {

  private static final Logger logger = LoggerFactory.getLogger(AgentSession.class);

  private final AgentGrpc agentGrpc;
  private final CredentialStore credentialStore;
  private final String registrationToken;
  private final List<Collector> collectors;

  private StreamObserver<AgentMessage> outboundObserver;

  private final Map<UUID, ObservationBatch> pendingBatches = new ConcurrentHashMap<>();

  private MonitorDescriptor descriptor;

  private ObservationService observationService;

  private volatile boolean running;

  private String agentVersion = "0.1.0";

  private ScheduledExecutorService senderScheduler;
  private ScheduledFuture<?> sendObservationsTask;
  private ScheduledFuture<?> sendHeartbeatTask;

  private long reconnectDelayMs = 1000;
  private static final long MAX_RECONNECT_DELAY_MS = 60_000;

  public AgentSession(
      String platformHost,
      int platformPort,
      String registrationToken,
      List<Collector> collectors,
      CredentialStore credentialStore) {

    this(platformHost, platformPort, registrationToken, collectors, credentialStore, null);
  }

  AgentSession(
      String platformHost,
      int platformPort,
      String registrationToken,
      List<Collector> collectors,
      CredentialStore credentialStore,
      AgentGrpc agentGrpc) {

    this.registrationToken = registrationToken;
    this.collectors = collectors;
    this.credentialStore = credentialStore;
    this.agentGrpc = agentGrpc != null ? agentGrpc : new AgentGrpc(this, platformHost, platformPort);
  }

  public void start() {
    this.running = true;
    connect();

    String credentials = credentialStore.load();
    if (credentials == null) {
      sendRegistration();
    } else {
      sendAuthentication();
    }
  }

  public void stop() {
    this.running = false;

    if (observationService != null) {
      observationService.stop();
      observationService = null;
    }

    shutdownSenderScheduler();

    agentGrpc.disconnect();
    logger.info("AgentSession - closed");
  }

  public void reconnect() {
    if (!running)
      return;

    logger.info("AgentSession - reconnect triggered, delay={}ms", reconnectDelayMs);
    agentGrpc.disconnect();

    try {
      Thread.sleep(reconnectDelayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
    long jitter = (long) (Math.random() * 1000);
    reconnectDelayMs += jitter;

    connect();
  }

  public void connect() {
    agentGrpc.connect();
  }

  public void setOutboundObserver(
      StreamObserver<AgentMessage> outboundObserver) {
    this.outboundObserver = outboundObserver;
  }

  public void send(AgentMessage message) {
    if (outboundObserver != null && running) {
      outboundObserver.onNext(message);
    }
  }

  public void sendRegistration() {
    AgentMessage msg = AgentMessage.newBuilder()
        .setRegistration(RegistrationRequest.newBuilder()
            .setRegistrationToken(registrationToken)
            .setAgentVersion(agentVersion)
            .build())
        .build();
    send(msg);
    logger.info("AgentSession - Sent registration request");
  }

  public void sendAuthentication() {
    String credential = credentialStore.load();
    if (credential == null || credential.isBlank()) {
      logger.warn("AgentSession - No credential stored, falling back to registration");
      sendRegistration();
      return;
    }
    AgentMessage msg = AgentMessage.newBuilder()
        .setAuthentication(AuthenticationRequest.newBuilder()
            .setCredential(credential)
            .build())
        .build();
    send(msg);
    logger.info("AgentSession - Sent authentication request");
  }

  public void sendHeartbeat() {
    send(AgentMessage.newBuilder()
        .setHeartbeat(Heartbeat.newBuilder()
            .setTimestamp(Timestamps.fromMillis(Instant.now().toEpochMilli()))
            .build())
        .build());
  }

  public void sendObservations() {
    if (!running)
      return;

    List<ObservationBatch> toSend = new ArrayList<>(pendingBatches.values());

    for (ObservationBatch batch : toSend) {
      AgentMessage msg = AgentMessage.newBuilder()
          .setObservations(batch)
          .build();
      send(msg);
    }
  }

  public void sendDescriptorApplied(String version, boolean success, String error) {
    send(AgentMessage.newBuilder()
        .setDescriptorApplied(DescriptorApplied.newBuilder()
            .setDescriptorVersion(version)
            .setSuccess(success)
            .setErrorMessage(error != null ? error : "")
            .build())
        .build());
  }

  public void handleRegistrationAccepted(RegistrationAccepted message) {
    String credential = message.getCredential();
    if (credential == null || credential.isBlank()) {
      logger.error("AgentSession - Empty credential from platform");
      return;
    }

    this.credentialStore.save(credential);
    this.descriptor = message.getMonitorDescriptor();
    logger.info("AgentSession - Registered, monitorId={}, version={}",
        descriptor.getId(), descriptor.getVersion());

    this.observationService = new ObservationService(
        this.pendingBatches,
        this.descriptor);
    this.observationService.start(collectors, this.descriptor);

    startSenderScheduler();

    reconnectDelayMs = 1000;

    sendDescriptorApplied(descriptor.getVersion(), true, null);
  }

  public void handleHeartbeatResponse(HeartbeatResponse message) {
    logger.debug("AgentSession - Heartbeat OK");
  }

  public void handleDescriptorUpdate(DescriptorUpdate message) {
    this.descriptor = message.getMonitorDescriptor();
    logger.info("AgentSession - Descriptor updated to version={}", descriptor.getVersion());

    if (observationService != null) {
      observationService.stop();
    }

    this.observationService = new ObservationService(
        this.pendingBatches,
        this.descriptor);
    this.observationService.start(collectors, this.descriptor);

    sendDescriptorApplied(descriptor.getVersion(), true, null);
  }

  public void handleObservationAcknowledgement(ObservationAcknowledgement message) {
    String batchIdStr = message.getBatchId();
    UUID batchId;
    try {
      batchId = UUID.fromString(batchIdStr);
    } catch (IllegalArgumentException e) {
      logger.warn("AgentSession - Invalid batchId: {}", batchIdStr);
      return;
    }

    if (message.getSuccess()) {
      pendingBatches.remove(batchId);
      logger.debug("AgentSession - Batch {} acknowledged", batchId);
    } else {
      ObservationBatch batch = pendingBatches.get(batchId);
      if (batch != null) {
        logger.warn("AgentSession - Batch {} failed, retrying", batchId);
        send(AgentMessage.newBuilder().setObservations(batch).build());
      } else {
        logger.warn("AgentSession - Failed batch {} not found for retry", batchId);
      }
    }
  }

  public void handleConnectionError(Throwable cause) {
    if (!running)
      return;
    logger.warn("AgentSession - Connection error: {}", cause.getMessage());
    reconnect();
  }

  public void handleConnectionClosed() {
    if (!running)
      return;
    logger.info("AgentSession - Connection closed by platform");
    reconnect();
  }

  public boolean isRunning() {
    return running;
  }

  public void awaitTermination() {
    while (running) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void startSenderScheduler() {
    shutdownSenderScheduler();

    this.senderScheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
          Thread t = new Thread(r, "agent-sender");
          t.setDaemon(true);
          return t;
        });

    this.sendObservationsTask = senderScheduler.scheduleWithFixedDelay(
        this::sendObservations,
        0, 1, TimeUnit.SECONDS);

    this.sendHeartbeatTask = senderScheduler.scheduleWithFixedDelay(
        this::sendHeartbeat,
        0, 30, TimeUnit.SECONDS);
  }

  private void shutdownSenderScheduler() {
    if (sendObservationsTask != null) {
      sendObservationsTask.cancel(false);
      sendObservationsTask = null;
    }
    if (sendHeartbeatTask != null) {
      sendHeartbeatTask.cancel(false);
      sendHeartbeatTask = null;
    }
    if (senderScheduler != null) {
      senderScheduler.shutdown();
      try {
        if (!senderScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
          senderScheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        senderScheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
      senderScheduler = null;
    }
  }
}
