package com.platform.uop.monitor.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.uop.monitor.entity.Monitor;

public interface MonitorRepository extends JpaRepository<Monitor, UUID> {

  Optional<Monitor> findByIdAndWorkspaceId(UUID monitorId, UUID workspaceId);

}
