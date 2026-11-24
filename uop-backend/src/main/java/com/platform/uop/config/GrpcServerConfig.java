package com.platform.uop.config;

import com.platform.uop.monitor.service.AgentGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

@Configuration
@Profile("!test")
public class GrpcServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final AgentGrpcService agentGrpcService;
    private final int grpcPort;
    private Server grpcServer;

    public GrpcServerConfig(
            AgentGrpcService agentGrpcService,
            @Value("${app.grpc.port:9090}") int grpcPort) {
        this.agentGrpcService = agentGrpcService;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void startGrpcServer() {
        try {
            grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(agentGrpcService)
                .build()
                .start();

            logger.info("gRPC server started on port {} (plaintext)", grpcPort);

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down gRPC server...");
                grpcServer.shutdown();
                try {
                    if (!grpcServer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        grpcServer.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    grpcServer.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }));

        } catch (IOException e) {
            logger.error("Failed to start gRPC server on port {}", grpcPort, e);
            throw new RuntimeException("Failed to start gRPC server", e);
        }
    }

    @PreDestroy
    public void stopGrpcServer() {
        if (grpcServer != null) {
            logger.info("Stopping gRPC server...");
            grpcServer.shutdown();
            try {
                if (!grpcServer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    grpcServer.shutdownNow();
                }
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}