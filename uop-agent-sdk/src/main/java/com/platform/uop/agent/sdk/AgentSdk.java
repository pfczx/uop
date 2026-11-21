package com.platform.uop.agent.sdk;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AgentSdk implements AutoCloseable {

  private final AgentSession agentSession;

  private AgentSdk(Builder builder) {
    CredentialStore credentialStore = new CredentialStore(
        builder.credentialsPath != null
            ? builder.credentialsPath
            : defaultCredentialsPath());

    this.agentSession = new AgentSession(
        builder.platformHost,
        builder.platformPort,
        builder.registrationToken,
        builder.monitorId,
        builder.collectors,
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
    agentSession.close();
  }

  private static Path defaultCredentialsPath() {
    return Path.of(
        System.getProperty("user.home"),
        ".uop-agent",
        "credentials");
  }

  public static class Builder {

    private String platformHost = "localhost";
    private int platformPort = 9090;

    private String registrationToken;
    private String monitorId;

    private final List<Collector> collectors = new ArrayList<>();

    private Path credentialsPath;

    public Builder platformHost(String platformHost) {
      this.platformHost = Objects.requireNonNull(platformHost);
      return this;
    }

    public Builder platformPort(int platformPort) {
      if (platformPort < 1 || platformPort > 65535) {
        throw new IllegalArgumentException(
            "Platform port must be between 1 and 65535");
      }

      this.platformPort = platformPort;
      return this;
    }

    public Builder registrationToken(String registrationToken) {
      this.registrationToken = registrationToken;
      return this;
    }

    public Builder monitorId(String monitorId) {
      this.monitorId = monitorId;
      return this;
    }

    public Builder collector(Collector collector) {
      collectors.add(Objects.requireNonNull(collector));
      return this;
    }

    public Builder collectors(List<Collector> collectors) {
      this.collectors.addAll(Objects.requireNonNull(collectors));
      return this;
    }

    public Builder credentialsPath(Path credentialsPath) {
      this.credentialsPath = Objects.requireNonNull(credentialsPath);
      return this;
    }

    public AgentSdk build() {
      if (platformHost.isBlank()) {
        throw new IllegalStateException(
            "Platform host must not be blank");
      }

      return new AgentSdk(this);
    }
  }
}
