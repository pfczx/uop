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
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
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

    public static void main(String[] args) {
        PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine");

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    UOP DEMO - AUTOMATED (TLS)                   ║");
        System.out.println("║  Starts platform, creates monitor, runs agent, cleans up      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        Runtime.getRuntime().addShutdownHook(new Thread(UopDemo::shutdown));

        try {
            database.start();
            System.out.println("[OK] PostgreSQL started");

            startSpring(database);
            startGrpcServerWithTls();

            // Auto-initialize demo data
            initializeDemoData();

            // Auto-start agent with TLS
            startRealAgentWithTls();

            // Keep running, show agent output
            System.out.println();
            System.out.println("[DEMO] Agent running with TLS. Press Ctrl+C to stop and cleanup...");
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

    private static void startGrpcServerWithTls() throws Exception {
        var agentGrpcService = context.getBean(com.platform.uop.monitor.service.AgentGrpcService.class);

        // Generate self-signed certificate for TLS
        SelfSignedCertificate cert = new SelfSignedCertificate();
        
        // Use GrpcSslContexts.forServer() which properly configures ALPN for HTTP/2
        SslContext sslContext = GrpcSslContexts.forServer(cert.certificate(), cert.privateKey()).build();

        // Create gRPC server with TLS
        grpcServer = NettyServerBuilder.forPort(9090)
            .sslContext(sslContext)
            .addService(agentGrpcService)
            .build()
            .start();

        System.out.println("[OK] gRPC server started on port 9090 with TLS");
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
            new Workspace("Demo Workspace", "Workspace created by UOP demo"));

        memberRepository.save(new WorkspaceMember(workspace, admin, WorkspaceRole.OWNER));
        memberRepository.save(new WorkspaceMember(workspace, regularUser, WorkspaceRole.MEMBER));

        System.out.println("[OK] Workspace created");

        monitorId = UUID.randomUUID();

        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId(monitorId.toString())
            .setName("Demo Monitor")
            .setDescription("Monitor created by UOP demo")
            .setVersion("1.0.0")
            .setActive(true)
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("disk.usage_percent")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("disk.free_bytes")
                .setValueType(ValueType.VALUE_TYPE_INT64)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("disk.total_bytes")
                .setValueType(ValueType.VALUE_TYPE_INT64)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .build();

        Monitor monitor = monitorRepository.save(new Monitor(workspace, descriptor));
        monitorId = monitor.getId();

        String registrationToken = credentialService.createRegistrationToken(monitorId);

        System.out.println("[OK] Monitor created");
        System.out.println("[OK] Registration token generated");
        System.out.println();
        System.out.println("Credentials: admin@uop.local / admin123");
        System.out.println("Registration token: " + registrationToken);
        System.out.println();
    }

    private static void startRealAgentWithTls() {
        if (monitorId == null) {
            System.err.println("[ERROR] No monitor ID available");
            return;
        }

        try {
            System.out.println("=== Starting Real Agent (DiskCollector) ===");

            // Working directory for exec:java is project root (/home/devpad/projekty/uop)
            String projectRoot = new java.io.File(".").getCanonicalPath();
            String agentJar = projectRoot + "/uop-agent/target/uop-agent-0.1.0-SNAPSHOT.jar";

            System.out.println("[AGENT] Starting agent with DiskCollector...");
            System.out.println("[AGENT] JAR: " + agentJar);
            System.out.println("[AGENT] Platform: localhost:9090");

            String token = context.getBean(AgentCredentialService.class).createRegistrationToken(monitorId);

            ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", agentJar,
                "--platform", "localhost:9090",
                "--token", token
            );
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);

            agentProcess = pb.start();

            // Stream agent output in real-time
            Thread outputThread = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(agentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[AGENT] " + line);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }, "agent-output-reader");
            outputThread.setDaemon(true);
            outputThread.start();

            System.out.println("[OK] Agent started (PID: " + agentProcess.pid() + ")");
            System.out.println("[INFO] Agent will register and send disk metrics every second");
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
            System.out.println("[INFO] Stopping agent...");
            agentProcess.destroy();
            try {
                if (!agentProcess.waitFor(5, TimeUnit.SECONDS)) {
                    agentProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[OK] Agent stopped");
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

        System.out.println("[OK] Platform stopped");
    }
}