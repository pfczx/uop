package com.platform.uop.monitor.controller;

import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.monitor.dto.UpdateMonitorRequest;
import com.platform.uop.monitor.service.MonitorService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/monitors")
@RequiredArgsConstructor
public class MonitorController {

  private final MonitorService monitorService;

  @PostMapping
  @PreAuthorize("@workspaceSecurity.canManageMembers(#workspaceId, authentication)")
  public ResponseEntity<MonitorResponse> create(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateMonitorRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(monitorService.create(request, workspaceId));
  }

  @PutMapping("/{monitorId}")
  @PreAuthorize("@workspaceSecurity.canManageMembers(#workspaceId, authentication)")
  public ResponseEntity<MonitorResponse> update(
      @PathVariable UUID workspaceId,
      @PathVariable UUID monitorId,
      @Valid @RequestBody UpdateMonitorRequest request) {

    return ResponseEntity.ok(
        monitorService.update(request, workspaceId, monitorId));
  }

  @DeleteMapping("/{monitorId}")
  @PreAuthorize("@workspaceSecurity.canManageMembers(#workspaceId, authentication)")
  public ResponseEntity<Void> remove(
      @PathVariable UUID workspaceId,
      @PathVariable UUID monitorId) {

    monitorService.remove(workspaceId, monitorId);

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{monitorId}/active")
  @PreAuthorize("@workspaceSecurity.canManageMembers(#workspaceId, authentication)")
  public ResponseEntity<MonitorResponse> setActive(
      @PathVariable UUID workspaceId,
      @PathVariable UUID monitorId) {

    return ResponseEntity.ok(
        monitorService.setActive(workspaceId, monitorId));
  }

  @PatchMapping("/{monitorId}/inactive")
  @PreAuthorize("@workspaceSecurity.canManageMembers(#workspaceId, authentication)")
  public ResponseEntity<MonitorResponse> setInactive(
      @PathVariable UUID workspaceId,
      @PathVariable UUID monitorId) {

    return ResponseEntity.ok(
        monitorService.setInactive(workspaceId, monitorId));
  }

  @PostMapping("/{monitorId}/installation-command")
  @PreAuthorize("@workspaceSecurity.canManageMembers(#workspaceId, authentication)")
  public ResponseEntity<String> generateInstallationCommand(
      @PathVariable UUID workspaceId,
      @PathVariable UUID monitorId) {

    return ResponseEntity.ok(
        monitorService.generateInstallationCommand(workspaceId, monitorId));
  }
}
