package com.platform.uop.workspace.service;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.dto.AddWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.FindWorkspaceMembersRequest;
import com.platform.uop.workspace.dto.RemoveWorkspaceMemeberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceMemberResponse;
import com.platform.uop.workspace.dto.WorkspaceResponse;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final UserRepository userRepository;

  private Workspace getWorkspace(UUID id) {
    return workspaceRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND,
            "Workspace not found"));
  }

  @Transactional
  public WorkspaceResponse create(CreateWorkspaceRequest request, UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND, "User not found"));

    Workspace workspace = new Workspace(
        request.name(),
        request.description());

    workspaceRepository.save(workspace);

    WorkspaceMember owner = new WorkspaceMember(
        workspace,
        user,
        WorkspaceRole.OWNER);

    workspaceMemberRepository.save(owner);

    return WorkspaceResponse.from(workspace);
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

  @Transactional(readOnly = true)
  public List<WorkspaceMemberResponse> findWorkspaceMembers(FindWorkspaceMembersRequest request, UUID id) {

    return workspaceMemberRepository.findAllByWorkspaceId(id)
        .stream()
        .map(WorkspaceMemberResponse::from)
        .toList();
  }

  public WorkspaceMemberResponse addMember(AddWorkspaceMemberRequest request, UUID id) {

    if (request.role() == WorkspaceRole.OWNER) {
      throw new ResponseStatusException(FORBIDDEN, "Cannot grant owner permission");
    }

    Workspace workspace = workspaceRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
            "Workspace not found"));

    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
            "User not found"));

    WorkspaceMember member = new WorkspaceMember(workspace, user, request.role());

    return WorkspaceMemberResponse
        .from(workspaceMemberRepository.save(member));
  }

  public WorkspaceMemberResponse updateMemberRole(UpdateWorkspaceMemberRequest request, UUID id) {

    if (request.role() == WorkspaceRole.OWNER) {
      throw new ResponseStatusException(FORBIDDEN, "Cannot grant owner permission");
    }

    WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserEmail(id, request.email())
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

    member.changeRole(request.role());

    return WorkspaceMemberResponse
        .from(workspaceMemberRepository.save(member));

  }

  public void removeMember(RemoveWorkspaceMemeberRequest request, UUID id) {

    WorkspaceMember member = workspaceMemberRepository
        .findByWorkspaceIdAndUserEmail(id, request.email())
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

    if (member.getRole() == WorkspaceRole.OWNER) {
      throw new ResponseStatusException(FORBIDDEN, "Cannot delete owner");
    }

    workspaceMemberRepository.delete(member);

  }

}
