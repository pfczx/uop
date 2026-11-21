package com.platform.uop.demo.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.AgentCredentialRepository;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.observation.entity.ObservationEntity;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.observation.service.ObservationService;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

  private final UserRepository userRepository;
  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final MonitorRepository monitorRepository;
  private final AgentCredentialRepository credentialRepository;
  private final ObservationRepository observationRepository;
  private final ObservationBatchRepository batchRepository;
  private final ObservationService observationService;
  private final AgentCredentialService agentCredentialService;
  private final PasswordEncoder passwordEncoder;

  @GetMapping("/status")
  public ResponseEntity<DevStatus> status() {
    return ResponseEntity.ok(new DevStatus(
        userRepository.count(),
        workspaceRepository.count(),
        monitorRepository.count(),
        batchRepository.count()));
  }

  @GetMapping("/ping")
  public ResponseEntity<String> ping() {
    return ResponseEntity.ok("pong");
  }

  @DeleteMapping("/clear")
  @Transactional
  public ResponseEntity<Void> clearDatabase() {
    observationRepository.deleteAll();
    batchRepository.deleteAll();
    credentialRepository.deleteAll();
    monitorRepository.deleteAll();
    memberRepository.deleteAll();
    workspaceRepository.deleteAll();
    userRepository.deleteAll();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users")
  public ResponseEntity<UserDto> addUser(
      @RequestParam String email,
      @RequestParam String password,
      @RequestParam(defaultValue = "USER") String role) {

    User user = User.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash(passwordEncoder.encode(password))
        .role(UserRole.valueOf(role))
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();

    User saved = userRepository.save(user);
    return ResponseEntity.ok(new UserDto(saved.getId(), saved.getEmail(), saved.getRole().name(), saved.getStatus().name()));
  }

  @GetMapping("/users")
  public ResponseEntity<List<UserDto>> listUsers() {
    List<UserDto> users = userRepository.findAll().stream()
        .map(u -> new UserDto(u.getId(), u.getEmail(), u.getRole().name(), u.getStatus().name()))
        .toList();
    return ResponseEntity.ok(users);
  }

  @PostMapping("/workspaces")
  public ResponseEntity<WorkspaceDto> addWorkspace(
      @RequestParam String name,
      @RequestParam String description) {

    Workspace ws = workspaceRepository.save(new Workspace(name, description));
    return ResponseEntity.ok(new WorkspaceDto(ws.getId(), ws.getName(), ws.getDescription()));
  }

  @GetMapping("/workspaces")
  public ResponseEntity<List<WorkspaceDto>> listWorkspaces() {
    List<WorkspaceDto> workspaces = workspaceRepository.findAll().stream()
        .map(w -> new WorkspaceDto(w.getId(), w.getName(), w.getDescription()))
        .toList();
    return ResponseEntity.ok(workspaces);
  }

  @PostMapping("/monitors")
  public ResponseEntity<MonitorDto> addMonitor(
      @RequestParam UUID workspaceId,
      @RequestParam String name) {

    Workspace workspace = workspaceRepository.findById(workspaceId)
        .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

    MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
        .setId(UUID.randomUUID().toString())
        .setName(name)
        .setDescription("Dev monitor")
        .setVersion("1.0")
        .setActive(true)
        .build();

    Monitor monitor = monitorRepository.save(new Monitor(workspace, descriptor));
    String token = agentCredentialService.createRegistrationToken(monitor.getId());

    return ResponseEntity.ok(new MonitorDto(
        monitor.getId(),
        monitor.getWorkspace().getId(),
        monitor.getDescriptor().getName(),
        monitor.getDescriptor().getActive(),
        token,
        monitor.getDescriptor().getName()));
  }

  @GetMapping("/monitors")
  public ResponseEntity<List<MonitorDto>> listMonitors() {
    List<MonitorDto> monitors = monitorRepository.findAll().stream()
        .map(m -> new MonitorDto(
            m.getId(),
            m.getWorkspace().getId(),
            m.getDescriptor().getName(),
            m.getDescriptor().getActive(),
            null,
            m.getDescriptor().getName()))
        .toList();
    return ResponseEntity.ok(monitors);
  }

  @GetMapping("/observations/{monitorId}")
  public ResponseEntity<List<ObservationDto>> listObservations(
      @PathVariable UUID monitorId) {

    Page<ObservationEntity> page = observationService.findByMonitor(
        monitorId, PageRequest.of(0, 50, Sort.by("observedAt").descending()));

    List<ObservationDto> dtos = page.getContent().stream()
        .map(e -> new ObservationDto(
            e.getId(),
            e.getObservation().getMetric(),
            e.getObservation().getValue().getDoubleValue(),
            e.getObservedAt()))
        .toList();

    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/observations/send")
  public ResponseEntity<ObservationResult> sendObservation(
      @RequestParam UUID monitorId,
      @RequestParam String metric,
      @RequestParam(defaultValue = "42.0") double value) {

    Monitor monitor = monitorRepository.findById(monitorId)
        .orElseThrow(() -> new IllegalArgumentException("Monitor not found"));

    Value obsValue = Value.newBuilder().setDoubleValue(value).build();

    Observation obs = Observation.newBuilder()
        .setMetric(metric)
        .setValue(obsValue)
        .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
            .setSeconds(Instant.now().getEpochSecond())
            .build())
        .build();

    ObservationBatch batch = ObservationBatch.newBuilder()
        .setBatchId("dev-batch-" + UUID.randomUUID())
        .addObservations(obs)
        .build();

    var result = observationService.saveBatch(monitorId, batch.getBatchId(), batch.getObservationsList());

    return ResponseEntity.ok(new ObservationResult(
        result.alreadyProcessed(),
        result.savedCount(),
        batch.getBatchId()));
  }

  @PostMapping("/observations/bulk")
  public ResponseEntity<BulkObservationResult> sendBulkObservations(
      @RequestParam UUID monitorId,
      @RequestParam(defaultValue = "1") int batches,
      @RequestParam(defaultValue = "100") int observationsPerBatch,
      @RequestParam(defaultValue = "cpu.usage") String metric,
      @RequestParam(defaultValue = "fixed") String mode,
      @RequestParam(required = false) Double minValue,
      @RequestParam(required = false) Double maxValue) {

    Monitor monitor = monitorRepository.findById(monitorId)
        .orElseThrow(() -> new IllegalArgumentException("Monitor not found"));

    int totalSaved = 0;
    int totalBatches = 0;

    boolean random = "random".equalsIgnoreCase(mode);
    double min = random && minValue != null ? minValue : 0;
    double max = random && maxValue != null ? maxValue : 100;

    for (int b = 0; b < batches; b++) {
      String batchId = "dev-bulk-" + UUID.randomUUID();

      ObservationBatch.Builder batchBuilder = ObservationBatch.newBuilder().setBatchId(batchId);

      for (int o = 0; o < observationsPerBatch; o++) {
        double value = random
            ? ThreadLocalRandom.current().nextDouble(min, max)
            : (minValue != null ? minValue : 42.0);
        Value obsValue = Value.newBuilder().setDoubleValue(value).build();

        Observation obs = Observation.newBuilder()
            .setMetric(metric)
            .setValue(obsValue)
            .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .build())
            .build();

        batchBuilder.addObservations(obs);
      }

      var result = observationService.saveBatch(
          monitorId, batchId, batchBuilder.build().getObservationsList());

      if (!result.alreadyProcessed()) {
        totalSaved += result.savedCount();
        totalBatches++;
      }
    }

    return ResponseEntity.ok(new BulkObservationResult(
        totalBatches, totalSaved));
  }

  @GetMapping("/agents/{monitorId}/token")
  public ResponseEntity<AgentTokenDto> registrationToken(@PathVariable UUID monitorId) {
    String token = agentCredentialService.createRegistrationToken(monitorId);
    return ResponseEntity.ok(new AgentTokenDto(monitorId, token));
  }

  // ---- DTOs ----

  public record DevStatus(long users, long workspaces, long monitors, long observationBatches) {}

  public record ObservationDto(UUID id, String metric, double value, Instant observedAt) {}

  public record UserDto(UUID id, String email, String role, String status) {}
  public record WorkspaceDto(UUID id, String name, String description) {}
  public record MonitorDto(UUID id, UUID workspaceId, String name, boolean active, String registrationToken, String descriptorName) {}
  public record ObservationResult(boolean alreadyProcessed, int savedCount, String batchId) {}
  public record BulkObservationResult(int batches, int totalObservations) {}
  public record AgentTokenDto(UUID monitorId, String registrationToken) {}
}
