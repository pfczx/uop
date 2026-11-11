package com.platform.uop.monitor.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.observation.service.ObservationService;
import com.platform.uop.proto.v1.AgentMessage;
import com.platform.uop.proto.v1.AgentServiceGrpc;
import com.platform.uop.proto.v1.HeartbeatResponse;
import com.platform.uop.proto.v1.PlatformMessage;
import com.platform.uop.proto.v1.RegistrationAccepted;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentGrpcService extends AgentServiceGrpc.AgentServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(AgentGrpcService.class);

  private final AgentConnectionService agentConnectionService;
  private final AgentCredentialService agentCredentialService;
  private final MonitorRepository monitorRepository;
  private final ObservationService observationService;

  @Override
  public StreamObserver<AgentMessage> connect(StreamObserver<PlatformMessage> responseObserver) {

    return new StreamObserver<>() {

      private AgentConnectionService.AgentSession session;
      private UUID monitorId;
      private boolean initialized;

      @Override
      public void onNext(AgentMessage message) {
        try {
          if (!initialized) {
            initializeConnection(message, responseObserver);
            return;
          }

          handleMessage(message);
        } catch (IllegalArgumentException exception) {
          fail(Status.UNAUTHENTICATED
              .withDescription(exception.getMessage())
              .withCause(exception)
              .asRuntimeException());
        } catch (Exception exception) {
          logger.error("Error handling agent message", exception);

          fail(Status.INTERNAL
              .withDescription("Failed to process agent message")
              .withCause(exception)
              .asRuntimeException());
        }
      }

      private void initializeConnection(
          AgentMessage message,
          StreamObserver<PlatformMessage> responseObserver) {

        String credential = null;

        switch (message.getMessageCase()) {
          case REGISTRATION -> {
            AgentCredentialService.RegisteredAgent registeredAgent = agentCredentialService.registerAgent(
                message.getRegistration().getRegistrationToken());

            monitorId = registeredAgent.monitorId();
            credential = registeredAgent.credential();
          }

          case AUTHENTICATION -> {
            monitorId = agentCredentialService.authenticate(
                message.getAuthentication().getCredential());
          }

          default -> throw Status.UNAUTHENTICATED
              .withDescription(
                  "The first message must contain registration or authentication data")
              .asRuntimeException();
        }

        Monitor monitor = monitorRepository.findById(monitorId)
            .orElseThrow(() -> Status.NOT_FOUND
                .withDescription("Monitor not found")
                .asRuntimeException());

        session = agentConnectionService.openConnection(
            monitorId, responseObserver);

        initialized = true;

        if (credential != null) {
          session.send(PlatformMessage.newBuilder()
              .setRegistrationAccepted(RegistrationAccepted.newBuilder()
                  .setAgentId(monitorId.toString())
                  .setCredential(credential)
                  .setMonitorDescriptor(monitor.getDescriptor())
                  .build())
              .build());

          logger.info("Agent registered for monitor {}", monitorId);
        } else {
          session.send(PlatformMessage.newBuilder()
              .setDescriptorUpdate(
                  com.platform.uop.proto.v1.DescriptorUpdate.newBuilder()
                      .setMonitorDescriptor(monitor.getDescriptor())
                      .build())
              .build());

          logger.info("Agent authenticated for monitor {}", monitorId);
        }
      }

      private void handleMessage(AgentMessage message) {
        switch (message.getMessageCase()) {
          case HEARTBEAT -> sendHeartbeatResponse();

          case OBSERVATIONS -> handleObservations(message);

          case DESCRIPTOR_APPLIED -> handleDescriptorApplied(message);

          case REGISTRATION -> throw Status.INVALID_ARGUMENT
              .withDescription("Registration can only be sent once")
              .asRuntimeException();

          case MESSAGE_NOT_SET -> throw Status.INVALID_ARGUMENT
              .withDescription("Agent message must contain a payload")
              .asRuntimeException();
        }
      }

      private void sendHeartbeatResponse() {
        session.send(PlatformMessage.newBuilder()
            .setHeartbeatResponse(HeartbeatResponse.newBuilder()
                .setServerTime(com.google.protobuf.util.Timestamps.fromMillis(
                    Instant.now().toEpochMilli()))
                .build())
            .build());
      }

      private void handleObservations(AgentMessage message) {
        var batch = message.getObservations();

        String batchId = batch.getBatchId();

        if (batchId.isBlank()) {
          throw Status.INVALID_ARGUMENT
              .withDescription("Observation batch ID is required")
              .asRuntimeException();
        }

        List<com.platform.uop.proto.v1.Observation> observations = batch.getObservationsList();

        if (observations.isEmpty()) {
          throw Status.INVALID_ARGUMENT
              .withDescription("Observation batch cannot be empty")
              .asRuntimeException();
        }

        observationService.saveBatch(
            monitorId,
            batchId,
            observations);

        session.send(PlatformMessage.newBuilder()
            .setObservationAcknowledgement(
                com.platform.uop.proto.v1.ObservationAcknowledgement
                    .newBuilder()
                    .setBatchId(batchId)
                    .setSuccess(true)
                    .setMessage("Observations saved")
                    .build())
            .build());
      }

      private void handleDescriptorApplied(AgentMessage message) {
        var applied = message.getDescriptorApplied();

        if (!applied.getSuccess()) {
          logger.warn(
              "Agent failed to apply descriptor for monitor {}: {}",
              monitorId,
              applied.getErrorMessage());
          return;
        }

        logger.info(
            "Agent applied descriptor version {} for monitor {}",
            applied.getDescriptorVersion(),
            monitorId);
      }

      private void fail(Throwable error) {
        if (session != null) {
          agentConnectionService.closeConnection(session);
          session.close(error);
        } else {
          responseObserver.onError(error);
        }
      }

      @Override
      public void onError(Throwable error) {
        if (session != null) {
          agentConnectionService.closeConnection(session);
        }

        logger.info("Agent connection closed with error for monitor {}",
            monitorId);
      }

      @Override
      public void onCompleted() {
        if (session != null) {
          agentConnectionService.closeConnection(session);
          session.complete();
        }

        logger.info("Agent disconnected from monitor {}", monitorId);
      }
    };
  }
}
