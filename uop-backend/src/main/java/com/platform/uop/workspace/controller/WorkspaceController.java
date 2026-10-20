package com.platform.uop.workspace.controller;

import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.workspace.dto.AddWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.FindWorkspaceMembersRequest;
import com.platform.uop.workspace.dto.RemoveWorkspaceMemeberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceMemberResponse;
import com.platform.uop.workspace.dto.WorkspaceResponse;
import com.platform.uop.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceResponse create(
      @Valid @RequestBody CreateWorkspaceRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return workspaceService.create(request, principal.getUser().getId());
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<WorkspaceResponse> findAll() {
    return workspaceService.findAll();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public WorkspaceResponse findById(@PathVariable UUID id) {
    return workspaceService.findById(id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or @workspaceSecurity.isOwner(#id, authentication)")
  public WorkspaceResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWorkspaceRequest request) {
    return workspaceService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or @workspaceSecurity.isOwner(#id, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    workspaceService.delete(id);
  }

  @GetMapping("/{id}/members")
  @PreAuthorize("hasRole('ADMIN') or @workspaceSecurity.canManageMembers(#id,authentication)")
  public List<WorkspaceMemberResponse> findWorkspaceMembers(
      @PathVariable UUID id,
      @Valid @RequestBody FindWorkspaceMembersRequest request) {
    return workspaceService.findWorkspaceMembers(request, id);
  }

  @PostMapping("/{id}/members")
  @PreAuthorize("hasRole('ADMIN') or @workspaceSecurity.canManageMembers(#id, authentication)")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceMemberResponse addMember(
      @PathVariable UUID id,
      @Valid @RequestBody AddWorkspaceMemberRequest request) {
    return workspaceService.addMember(request, id);
  }

  @PatchMapping("/{id}/members/role")
  @PreAuthorize("hasRole('ADMIN') or @workspaceSecurity.canManageMembers(#id, authentication)")
  public WorkspaceMemberResponse updateMemberRole(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWorkspaceMemberRequest request) {
    return workspaceService.updateMemberRole(request, id);
  }

  @DeleteMapping("/{id}/members")
  @PreAuthorize("hasRole('ADMIN') or @workspaceSecurity.canManageMembers(#id, authentication)")
  public void removeMember(
      @PathVariable UUID id,
      @Valid @RequestBody RemoveWorkspaceMemeberRequest request) {
    workspaceService.removeMember(request, id);
  }

}
