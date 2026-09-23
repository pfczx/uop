package com.platform.uop.workspace.controller;

import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceResponse;
import com.platform.uop.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceResponse create(
      @Valid @RequestBody CreateWorkspaceRequest request) {
    return workspaceService.create(request);
  }

  @GetMapping
  public List<WorkspaceResponse> findAll() {
    return workspaceService.findAll();
  }

  @GetMapping("/{id}")
  public WorkspaceResponse findById(@PathVariable UUID id) {
    return workspaceService.findById(id);
  }

  @PutMapping("/{id}")
  public WorkspaceResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWorkspaceRequest request) {
    return workspaceService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    workspaceService.delete(id);
  }
}
