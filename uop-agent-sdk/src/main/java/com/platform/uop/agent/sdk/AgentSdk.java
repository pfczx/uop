package com.platform.uop.agent.sdk;

import com.platform.uop.agent.sdk.Collector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AgentSdk implements AutoCloseable {

  private final AgentSession agentSession;

  private AgentSdk(Builder builder) {
    Path credentialsPath = builder.credentialsPath != null
        ? builder.credentialsPath
        : Path.of(
            System.getProperty("user.home"),
            ".uop-agent",
            "credentials");

    CredentialStore credentialStore = new CredentialStore(credentialsPath);

    this.agentSession = new AgentSession(
        builder.platformHost,
        builder.platformPort,
        builder.registrationToken,
        List.copyOf(builder.collectors),
        credentialStore);
  }

  public static Builder builder() {
    return new Builder();
  }

  public void start() {
    agentSession.start();
  }

  public void awaitTermination() {
    agentSession.awaitTermination();
  }

  public boolean isRunning() {
    return agentSession.isRunning();
  }

  @Override
  public void close() {
    agentSession.stop();
  }

  public static class Builder {

    private String platformHost = "localhost";
    private int platformPort = 9090;
    private String registrationToken;
    private Path credentialsPath;

    private final List<Collector> collectors = new ArrayList<>();

    private Builder() {
    }

    public Builder platformHost(String platformHost) {
      this.platformHost = platformHost;
      return this;
    }

    public Builder platformPort(int platformPort) {
      this.platformPort = platformPort;
      return this;
    }

    public Builder registrationToken(String registrationToken) {
      this.registrationToken = registrationToken;
      return this;
    }

    public Builder credentialsPath(Path credentialsPath) {
      this.credentialsPath = credentialsPath;
      return this;
    }

    public Builder collector(Collector collector) {
      if (collector == null) {
        throw new IllegalArgumentException(
            "Collector must not be null");
      }

      collectors.add(collector);
      return this;
    }

    public Builder collectors(List<Collector> collectors) {
      if (collectors == null) {
        throw new IllegalArgumentException(
            "Collectors must not be null");
      }

      collectors.forEach(this::collector);
      return this;
    }

    public AgentSdk build() {
      if (platformHost == null || platformHost.isBlank()) {
        throw new IllegalArgumentException(
            "Platform host must not be blank");
      }

      if (platformPort < 1 || platformPort > 65535) {
        throw new IllegalArgumentException(
            "Platform port must be between 1 and 65535");
      }

      return new AgentSdk(this);
    }
  }
}
