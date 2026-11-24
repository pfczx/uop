package com.platform.uop.demo;

import com.platform.uop.UopApplication;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.ValueType;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

public class UopDemo {

    private static ConfigurableApplicationContext context;
    private static Server grpcServer;
    private static Process agentProcess;
    private static UUID monitorId;
    private static Path tempCredentialDir;

    public static void main(String[] args) {
        PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine");

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    UOP DEMO - AUTOMATED                        ║");
        System.out.println("║  Starts platform, creates monitor, runs agent, cleans up      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("This demo will:");
        System.out.println("  1. Start PostgreSQL database");
        System.out.println("  2. Start Spring Boot + gRPC server (plaintext)");
        System.out.println("  3. Create demo data (users, workspace, monitor)");
        System.out.println("  4. Start real agent (DiskCollector) collecting metrics");
        System.out.println("  5. Stream agent logs showing registration, heartbeats, metrics");
        System.out.println("  6. Clean up on Ctrl+C");
        System.out.println();

        Runtime.getRuntime().addShutdownHook(new Thread(UopDemo::shutdown));

        try {
            database.start();
            log("[PLATFORM] PostgreSQL started");

            // Create temporary credential directory for demo
            tempCredentialDir = java.nio.file.Files.createTempDirectory("uop-demo-credentials-");
            log("[DEMO] Temporary credential directory: " + tempCredentialDir);

            startSpring(database);
            startGrpcServer();

            // Auto-initialize demo data
            initializeDemoData();

            // Auto-start agent
            startRealAgent();

            // Keep running, show agent output
            System.out.println();
            log("[DEMO] Agent running. Press Ctrl+C to stop and cleanup...");
            System.out.println();

            // Wait for shutdown signal
            Thread.currentThread().join();

        } catch (Exception exception) {
            System.err.println("[ERROR] Demo failed: " + exception.getMessage());
            exception.printStackTrace();
        } finally {
            shutdown();

            if (database.isRunning()) {
                database.stop();
            }

            System.out.println("[OK] Demo environment stopped.");
        }
    }

    private static void log(String message) {
        System.out.println(message);
    }

    private static void logPlatform(String message) {
        System.out.println("[PLATFORM] " + message);
    }

    private static void logAgent(String message) {
        System.out.println("[AGENT] " + message);
    }

    private static void logDemo(String message) {
        System.out.println("[DEMO] " + message);
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

        logPlatform("Spring Boot application started");
        logPlatform("Flyway migrations applied");
    }

    private static void startGrpcServer() throws Exception {
        var agentGrpcService = context.getBean(com.platform.uop.monitor.service.AgentGrpcService.class);

        grpcServer = ServerBuilder.forPort(9090)
            .addService(agentGrpcService)
            .build()
            .start();

        logPlatform("gRPC server started on port 9090 (plaintext)");
    }

    private static void initializeDemoData() {
        System.out.println();
        System.out.println("=== Initializing Demo Data ===");

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
            .passwordHash(new BCryptPasswordEncoder().encode("admin123"))
            .role(UserRole.ADMIN)
            .status(UserStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

        User regularUser = userRepository.save(User.builder()
            .id(UUID.randomUUID())
            .email("user@uop.local")
            .passwordHash(new BCryptPasswordEncoder().encode("user123"))
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

        logPlatform("Admin user created: admin@uop.local / admin123");
        logPlatform("Regular user created: user@uop.local / user123");

        Workspace workspace = workspaceRepository.save(
            new Workspace("Demo Workspace", "Workspace created by UOP demo"));

        memberRepository.save(new WorkspaceMember(workspace, admin, WorkspaceRole.OWNER));
        memberRepository.save(new WorkspaceMember(workspace, regularUser, WorkspaceRole.MEMBER));

        logPlatform("Workspace created: Demo Workspace");

        monitorId = UUID.randomUUID();

        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setName("Demo Monitor")
            .setDescription("Monitor created by UOP demo")
            .setVersion("1.0.0")
            .setActive(true)
            .addMetrics(com.platform.uop.proto.v1.MetricDescriptor.newBuilder()
                .setName("disk.usage_percent")
                .setValueType(com.platform.uop.proto.v1.ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(com.platform.uop.proto.v1.MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .addMetrics(com.platform.uop.proto.v1.MetricDescriptor.newBuilder()
                .setName("disk.free_bytes")
                .setValueType(com.platform.uop.proto.v1.ValueType.VALUE_TYPE_INT64)
                .setMetricType(com.platform.uop.proto.v1.MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .addMetrics(com.platform.uop.proto.v1.MetricDescriptor.newBuilder()
                .setName("disk.total_bytes")
                .setValueType(com.platform.uop.proto.v1.ValueType.VALUE_TYPE_INT64)
                .setMetricType(com.platform.uop.proto.v1.MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .build();

        Monitor monitor = monitorRepository.save(new Monitor(workspace, descriptor));
        monitorId = monitor.getId();

        String registrationToken = context.getBean(AgentCredentialService.class).createRegistrationToken(monitorId);

        logPlatform("Monitor created: " + monitorId);
        logPlatform("Registration token generated");
        System.out.println();
        System.out.println("Credentials: admin@uop.local / admin123");
        System.out.println("Registration token: " + registrationToken);
        System.out.println();
    }

    private static void startRealAgent() {
        if (monitorId == null) {
            System.err.println("[ERROR] No monitor ID available");
            return;
        }

        try {
            logDemo("=== Starting Real Agent (DiskCollector) ===");

            String projectRoot = new java.io.File(".").getCanonicalPath();
            String agentJar = projectRoot + "/uop-agent/target/uop-agent-0.1.0-SNAPSHOT.jar";

            logAgent("Starting DiskCollector agent...");
            logAgent("JAR: " + agentJar);
            logAgent("Platform: localhost:9090 (plaintext)");

            String token = context.getBean(AgentCredentialService.class).createRegistrationToken(monitorId);
            Path credFile = tempCredentialDir.resolve("credentials");

            ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", agentJar,
                "--platform", "localhost:9090",
                "--token", token,
                "--cred-path", tempCredentialDir.resolve("credentials").toString()
            );
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);

            agentProcess = pb.start();

            // Stream agent output in real-time with clear labels
            Thread outputThread = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(agentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Show all agent output with clear labeling
                        if (line.contains("Batch") || line.contains("acknowledged") || 
                            line.contains("Heartbeat") || line.contains("Heartbeat OK") ||
                            line.contains("Connected") || line.contains("Disconnected") ||
                            line.contains("Registered") || line.contains("Registered") ||
                            line.contains("AgentSession") || line.contains("AgentGrpc") ||
                            line.contains("Observation") || line.contains("Metric") ||
                            line.contains("DiskCollector") || line.contains("collect") ||
                            line.contains("ERROR") || line.contains("WARN") ||
                            line.contains("Sent registration") || line.contains("Sent authentication") ||
                            line.contains("Sent heartbeat") || line.contains("Sent observations") ||
                            line.contains("Batch") || line.contains("acknowledged")) {
                            System.out.println("[AGENT] " + line);
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }, "agent-output-reader");
            outputThread.setDaemon(true);
            outputThread.start();

            logDemo("Agent started (PID: " + agentProcess.pid() + ")");
            logDemo("Agent will register, send heartbeats, and collect disk metrics every second");
            System.out.println();

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to start agent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void shutdown() {
        System.out.println();
        System.out.println("=== Shutting Down ===");

        if (agentProcess != null && agentProcess.isAlive()) {
            logDemo("Stopping agent...");
            agentProcess.destroy();
            try {
                if (!agentProcess.waitFor(5, TimeUnit.SECONDS)) {
                    agentProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logDemo("Agent stopped");
        }

        if (grpcServer != null) {
            grpcServer.shutdown();
            try {
                if (!grpcServer.awaitTermination(3, TimeUnit.SECONDS)) {
                    grpcServer.shutdownNow();
                }
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (context != null) {
            context.close();
        }

        // Clean up temporary credential directory
        if (tempCredentialDir != null) {
            try {
                Files.walk(tempCredentialDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("[WARN] Failed to delete " + path + ": " + e.getMessage());
                        }
                    });
                logDemo("Temporary credential directory cleaned up");
            } catch (IOException e) {
                System.err.println("[WARN] Failed to clean up credential directory: " + e.getMessage());
            }
        }

        if (context != null) {
            context.close();
        }

        logPlatform("Platform stopped");
    }
}