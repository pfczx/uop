package com.platform.uop.workspace.repository;

import com.platform.uop.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  boolean existsByName(String name);
}
