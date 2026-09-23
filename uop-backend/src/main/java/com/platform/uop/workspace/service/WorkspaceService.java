package com.platform.uop.workspace.service;

import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceResponse;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {

  private final WorkspaceRepository workspaceRepository;

  public WorkspaceResponse create(CreateWorkspaceRequest request) {
    Workspace workspace = new Workspace(
        request.name(),
        request.description());

    return WorkspaceResponse.from(
        workspaceRepository.save(workspace));
  }

  @Transactional(readOnly = true)
  public List<WorkspaceResponse> findAll() {
    return workspaceRepository.findAll()
        .stream()
        .map(WorkspaceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public WorkspaceResponse findById(UUID id) {
    return WorkspaceResponse.from(getWorkspace(id));
  }

  public WorkspaceResponse update(
      UUID id,
      UpdateWorkspaceRequest request) {
    Workspace workspace = getWorkspace(id);

    workspace.update(
        request.name(),
        request.description());

    return WorkspaceResponse.from(workspace);
  }

  public void delete(UUID id) {
    Workspace workspace = getWorkspace(id);
    workspaceRepository.delete(workspace);
  }

  private Workspace getWorkspace(UUID id) {
    return workspaceRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND,
            "Workspace not found"));
  }
}
