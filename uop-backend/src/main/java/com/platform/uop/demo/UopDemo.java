package com.platform.uop.demo;

import com.google.protobuf.util.Timestamps;
import com.platform.uop.UopApplication;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.monitor.service.AgentGrpcService;
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
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Scanner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

public class UopDemo {

  private static final Scanner SCANNER = new Scanner(System.in);

  private static ConfigurableApplicationContext context;
  private static Server grpcServer;
  private static ManagedChannel grpcChannel;

  private static UUID monitorId;
  private static String registrationToken;

  public static void main(String[] args) {
    PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine");

    try {
      System.out.println("Starting temporary PostgreSQL...");
      database.start();

      startSpring(database);
      startGrpcServer();

      runMenu();

    } catch (Exception exception) {
      System.err.println("Demo failed: " + exception.getMessage());
      exception.printStackTrace();

    } finally {
      shutdown();

      if (database.isRunning()) {
        database.stop();
      }

      System.out.println("Demo environment stopped.");
    }
  }

  private static void startSpring(PostgreSQLContainer<?> database) {
    System.setProperty("spring.datasource.url", database.getJdbcUrl());
    System.setProperty("spring.datasource.username", database.getUsername());
    System.setProperty("spring.datasource.password", database.getPassword());
    System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");

    System.setProperty("spring.flyway.url", database.getJdbcUrl());
    System.setProperty("spring.flyway.user", database.getUsername());
    System.setProperty("spring.flyway.password", database.getPassword());

    context = new SpringApplicationBuilder(UopApplication.class)
        .web(WebApplicationType.SERVLET)
        .properties(
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
            "spring.main.banner-mode=off")
        .run();

    System.out.println("[OK] Spring application started");
    System.out.println("[OK] Flyway migrations applied");
  }

  private static void startGrpcServer() throws Exception {
    AgentGrpcService agentGrpcService = context.getBean(AgentGrpcService.class);

    grpcServer = ServerBuilder.forPort(9090)
        .addService(agentGrpcService)
        .build()
        .start();

    System.out.println("[OK] gRPC server started on port 9090");

    grpcChannel = ManagedChannelBuilder.forAddress("localhost", 9090)
        .usePlaintext()
        .build();

    System.out.println("[OK] gRPC channel created");
  }

  private static void runMenu() {
    boolean running = true;

    while (running) {
      System.out.println();
      System.out.println("================================");
      System.out.println("          UOP DEMO");
      System.out.println("================================");
      System.out.println("1. Initialize demo data");
      System.out.println("2. Simulate agent communication");
      System.out.println("3. Exit");
      System.out.print("Select option: ");

      String option = SCANNER.nextLine().trim();

      try {
        switch (option) {
          case "1" -> initializeDemoData();
          case "2" -> simulateAgent();
          case "3" -> running = false;
          default -> System.out.println("Invalid option.");
        }
      } catch (Exception exception) {
        System.err.println("Operation failed: " + exception.getMessage());
        exception.printStackTrace();
      }
    }
  }

  private static void initializeDemoData() {
    if (monitorId != null) {
      System.out.println("[INFO] Demo data has already been initialized.");
      return;
    }

    UserRepository userRepository = context.getBean(UserRepository.class);

    WorkspaceRepository workspaceRepository = context.getBean(WorkspaceRepository.class);

    WorkspaceMemberRepository memberRepository = context.getBean(WorkspaceMemberRepository.class);

    MonitorRepository monitorRepository = context.getBean(MonitorRepository.class);

    AgentCredentialService credentialService = context.getBean(AgentCredentialService.class);

    Instant now = Instant.now();
    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    User admin = userRepository.save(User.builder()
        .id(UUID.randomUUID())
        .email("admin@uop.local")
        .passwordHash(passwordEncoder.encode("admin123"))
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(now)
        .updatedAt(now)
        .build());

    User regularUser = userRepository.save(User.builder()
        .id(UUID.randomUUID())
        .email("user@uop.local")
        .passwordHash(passwordEncoder.encode("user123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(now)
        .updatedAt(now)
        .build());

    System.out.println("[OK] Admin user created");
    System.out.println("[OK] Regular user created");

    Workspace workspace = workspaceRepository.save(
        new Workspace(
            "Demo Workspace",
            "Workspace created by UOP demo"));

    memberRepository.save(
        new WorkspaceMember(workspace, admin, WorkspaceRole.OWNER));

    memberRepository.save(
        new WorkspaceMember(workspace, regularUser, WorkspaceRole.MEMBER));

    System.out.println("[OK] Workspace created");
    System.out.println("[OK] Workspace members created");

    monitorId = UUID.randomUUID();

    MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
        .setId(monitorId.toString())
        .setName("Demo Monitor")
        .setDescription("Monitor created by UOP demo")
        .setVersion("1.0.0")
        .setActive(true)
        .build();

    Monitor monitor = monitorRepository.save(
        new Monitor(workspace, descriptor));

    monitorId = monitor.getId();

    registrationToken = credentialService.createRegistrationToken(monitorId);

    System.out.println("[OK] Monitor created");
    System.out.println("[OK] Registration token generated");
    System.out.println();
    System.out.println("Demo credentials:");
    System.out.println("Admin: admin@uop.local / admin123");
    System.out.println("User:  user@uop.local / user123");
  }

  private static void simulateAgent() throws Exception {
    if (monitorId == null || registrationToken == null) {
      System.out.println("[ERROR] Initialize demo data first (option 1).");
      return;
    }

    SimulatedAgent agent = new SimulatedAgent(grpcChannel);

    System.out.println();
    System.out.println("========== AGENT SIMULATION ==========");

    System.out.println("[AGENT] Connecting to platform...");
    agent.connect();

    System.out.println("[AGENT] Sending registration request...");
    agent.register(registrationToken);

    System.out.println("[AGENT] Registration accepted");
    System.out.println("[AGENT] Credential received");

    System.out.println("[AGENT] Sending heartbeat...");
    agent.sendHeartbeat();
    System.out.println("[PLATFORM] Heartbeat acknowledged");

    System.out.println("[AGENT] Sending observation batch...");
    agent.sendObservations();
    System.out.println("[PLATFORM] Observation batch acknowledged");

    System.out.println("[AGENT] Disconnecting...");
    agent.disconnect();

    System.out.println();
    System.out.println("[AGENT] Reconnecting...");
    agent.connect();

    System.out.println("[AGENT] Sending authentication request...");
    agent.authenticate();

    System.out.println("[PLATFORM] Authentication successful");
    System.out.println("[PLATFORM] Current descriptor received");

    agent.confirmDescriptor();
    agent.disconnect();

    System.out.println();
    System.out.println("[OK] Agent simulation completed");
  }

  private static void shutdown() {
    if (grpcChannel != null) {
      grpcChannel.shutdown();

      try {
        if (!grpcChannel.awaitTermination(3, TimeUnit.SECONDS)) {
          grpcChannel.shutdownNow();
        }
      } catch (InterruptedException exception) {
        grpcChannel.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    if (grpcServer != null) {
      grpcServer.shutdown();

      try {
        if (!grpcServer.awaitTermination(3, TimeUnit.SECONDS)) {
          grpcServer.shutdownNow();
        }
      } catch (InterruptedException exception) {
        grpcServer.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    if (context != null) {
      context.close();
    }
  }

  private static final class SimulatedAgent {

    private final AgentServiceGrpc.AgentServiceStub stub;

    private StreamObserver<AgentMessage> requestObserver;
    private BlockingQueue<PlatformMessage> responses;
    private AtomicReference<Throwable> streamError;

    private String credential;
    private String descriptorVersion = "1.0.0";

    private SimulatedAgent(ManagedChannel channel) {
      this.stub = AgentServiceGrpc.newStub(channel);
    }

    private void connect() {
      responses = new LinkedBlockingQueue<>();
      streamError = new AtomicReference<>();

      requestObserver = stub.connect(new StreamObserver<>() {
        @Override
        public void onNext(PlatformMessage message) {
          responses.offer(message);
        }

        @Override
        public void onError(Throwable error) {
          streamError.set(error);
        }

        @Override
        public void onCompleted() {
          // The server completed the stream.
        }
      });
    }

    private void register(String token) {
      requestObserver.onNext(AgentMessage.newBuilder()
          .setRegistration(RegistrationRequest.newBuilder()
              .setRegistrationToken(token)
              .build())
          .build());

      PlatformMessage response = awaitResponse();

      if (response.getMessageCase() != PlatformMessage.MessageCase.REGISTRATION_ACCEPTED) {
        throw new IllegalStateException(
            "Expected RegistrationAccepted but received "
                + response.getMessageCase());
      }

      RegistrationAccepted accepted = response.getRegistrationAccepted();

      credential = accepted.getCredential();

      if (credential.isBlank()) {
        throw new IllegalStateException(
            "Platform returned an empty agent credential");
      }
    }

    private void authenticate() {
      requestObserver.onNext(AgentMessage.newBuilder()
          .setAuthentication(AuthenticationRequest.newBuilder()
              .setCredential(credential)
              .build())
          .build());

      PlatformMessage response = awaitResponse();

      if (response.getMessageCase() != PlatformMessage.MessageCase.DESCRIPTOR_UPDATE) {
        throw new IllegalStateException(
            "Expected DescriptorUpdate but received "
                + response.getMessageCase());
      }

      DescriptorUpdate update = response.getDescriptorUpdate();
      descriptorVersion = update.getMonitorDescriptor().getVersion();
    }

    private void sendHeartbeat() {
      requestObserver.onNext(AgentMessage.newBuilder()
          .setHeartbeat(Heartbeat.newBuilder()
              .setTimestamp(Timestamps.fromMillis(
                  System.currentTimeMillis()))
              .build())
          .build());

      PlatformMessage response = awaitResponse();

      if (response.getMessageCase() != PlatformMessage.MessageCase.HEARTBEAT_RESPONSE) {
        throw new IllegalStateException(
            "Expected HeartbeatResponse but received "
                + response.getMessageCase());
      }

      HeartbeatResponse heartbeatResponse = response.getHeartbeatResponse();

      System.out.println(
          "[AGENT] Server time: "
              + Timestamps.toString(heartbeatResponse.getServerTime()));
    }

    private void sendObservations() {
      Observation cpuObservation = Observation.newBuilder()
          .setMetric("system.cpu.usage")
          .setValue(Value.newBuilder()
              .setDoubleValue(42.7)
              .build())
          .setTimestamp(Timestamps.fromMillis(
              System.currentTimeMillis()))
          .build();

      Observation memoryObservation = Observation.newBuilder()
          .setMetric("system.memory.usage")
          .setValue(Value.newBuilder()
              .setDoubleValue(68.2)
              .build())
          .setTimestamp(Timestamps.fromMillis(
              System.currentTimeMillis()))
          .build();

      ObservationBatch batch = ObservationBatch.newBuilder()
          .setBatchId(UUID.randomUUID().toString())
          .addObservations(cpuObservation)
          .addObservations(memoryObservation)
          .build();

      requestObserver.onNext(AgentMessage.newBuilder()
          .setObservations(batch)
          .build());

      PlatformMessage response = awaitResponse();

      if (response.getMessageCase() != PlatformMessage.MessageCase.OBSERVATION_ACKNOWLEDGEMENT) {
        throw new IllegalStateException(
            "Expected ObservationAcknowledgement but received "
                + response.getMessageCase());
      }

      ObservationAcknowledgement acknowledgement = response.getObservationAcknowledgement();

      if (!acknowledgement.getSuccess()) {
        throw new IllegalStateException(
            "Observation batch rejected: "
                + acknowledgement.getMessage());
      }

      System.out.println(
          "[AGENT] Batch " + acknowledgement.getBatchId()
              + " accepted");
    }

    private void confirmDescriptor() {
      requestObserver.onNext(AgentMessage.newBuilder()
          .setDescriptorApplied(DescriptorApplied.newBuilder()
              .setDescriptorVersion(descriptorVersion)
              .setSuccess(true)
              .build())
          .build());
    }

    private void disconnect() {
      if (requestObserver != null) {
        requestObserver.onCompleted();
        requestObserver = null;
      }
    }

    private PlatformMessage awaitResponse() {
      try {
        PlatformMessage response = responses.poll(5, TimeUnit.SECONDS);

        if (response != null) {
          return response;
        }

        Throwable error = streamError.get();

        if (error != null) {
          throw new IllegalStateException(
              "gRPC stream failed", error);
        }

        throw new IllegalStateException(
            "Timed out waiting for platform response");

      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for platform response",
            exception);
      }
    }
  }
}
