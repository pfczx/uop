package com.platform.uop.monitor.dto;

import java.time.Instant;
import java.util.UUID;

import com.platform.uop.monitor.entity.Monitor;

public record MonitorResponse(
    UUID id,
    UUID workspaceId,
    String description,
    Instant createdAt,
    Instant updatedAt) {

  public static MonitorResponse from(Monitor monitor) {
    return new MonitorResponse(
        monitor.getId(),
        monitor.getWorkspace().getId(),
        monitor.getDescriptor().getDescription(),
        monitor.getCreatedAt(),
        monitor.getUpdatedAt());
  }
}
