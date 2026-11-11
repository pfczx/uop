package com.platform.uop.monitor.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.platform.uop.monitor.entity.AgentCredential;
import com.platform.uop.monitor.repository.AgentCredentialRepository;
import com.platform.uop.monitor.repository.MonitorRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentCredentialService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final AgentCredentialRepository credentialRepository;
  private final MonitorRepository monitorRepository;
  private final Duration registrationTokenLifetime;

  public AgentCredentialService(
      AgentCredentialRepository credentialRepository,
      MonitorRepository monitorRepository,
      @Value("${registration-token-lifetime:PT15M}") Duration registrationTokenLifetime) {
    this.credentialRepository = credentialRepository;
    this.monitorRepository = monitorRepository;
    this.registrationTokenLifetime = registrationTokenLifetime;
  }

  @Transactional
  public String createRegistrationToken(UUID monitorId) {
    var monitor = monitorRepository.findById(monitorId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND, "Monitor not found"));

    AgentCredential credential = credentialRepository
        .findByMonitorIdForUpdate(monitorId)
        .orElseGet(() -> new AgentCredential(monitor));

    String registrationToken = generateSecret();

    credential.setRegistrationToken(
        hashSecret(registrationToken),
        Instant.now().plus(registrationTokenLifetime));

    credentialRepository.save(credential);

    return registrationToken;
  }

  @Transactional
  public RegisteredAgent registerAgent(String registrationToken) {
    if (registrationToken == null || registrationToken.isBlank()) {
      throw new ResponseStatusException(
          UNAUTHORIZED, "Invalid registration token");
    }

    AgentCredential credential = credentialRepository
        .findByRegistrationTokenHashForUpdate(
            hashSecret(registrationToken))
        .orElseThrow(() -> new ResponseStatusException(
            UNAUTHORIZED, "Invalid registration token"));

    if (!credential.isRegistrationTokenValid(Instant.now())) {
      throw new ResponseStatusException(
          UNAUTHORIZED, "Registration token has expired");
    }

    String agentCredential = generateSecret();

    credential.consumeRegistrationToken();
    credential.setCredential(hashSecret(agentCredential));

    credentialRepository.save(credential);

    return new RegisteredAgent(
        credential.getMonitor().getId(),
        agentCredential);
  }

  @Transactional(readOnly = true)
  public UUID authenticate(String agentCredential) {
    if (agentCredential == null || agentCredential.isBlank()) {
      throw new ResponseStatusException(
          UNAUTHORIZED, "Invalid agent credential");
    }

    AgentCredential credential = credentialRepository
        .findByCredentialHash(hashSecret(agentCredential))
        .orElseThrow(() -> new ResponseStatusException(
            UNAUTHORIZED, "Invalid agent credential"));

    if (!credential.isCredentialActive()) {
      throw new ResponseStatusException(
          UNAUTHORIZED, "Agent credential has been revoked");
    }

    return credential.getMonitor().getId();
  }

  @Transactional
  public void revokeAgentCredentials(UUID monitorId) {
    credentialRepository.findByMonitorIdForUpdate(monitorId)
        .ifPresent(credential -> {
          credential.revoke();
          credentialRepository.save(credential);
        });
  }

  private static String generateSecret() {
    byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
    SECURE_RANDOM.nextBytes(bytes);

    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes);
  }

  private static String hashSecret(String secret) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(
          secret.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
          "SHA-256 algorithm is not available", exception);
    }
  }

  public record RegisteredAgent(
      UUID monitorId,
      String credential) {
  }
}
