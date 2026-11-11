package com.platform.uop.unit.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
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
import com.platform.uop.workspace.service.WorkspaceService;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

  @Mock
  private WorkspaceRepository workspaceRepository;

  @Mock
  private WorkspaceMemberRepository workspaceMemberRepository;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private WorkspaceService workspaceService;

  private static User user(UUID id, UserRole role, UserStatus status) {
    return User.builder()
        .id(id)
        .email("user@example.com")
        .passwordHash("hash")
        .role(role)
        .status(status)
        .createdAt(Instant.now().minusSeconds(60))
        .updatedAt(Instant.now().minusSeconds(60))
        .build();
  }

  private static User user() {
    return user(UUID.randomUUID(), UserRole.USER, UserStatus.ACTIVE);
  }

  private static Workspace workspace(UUID id, String name, String description) {
    Workspace ws = new Workspace(name, description);
    try {
      java.lang.reflect.Field field = Workspace.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(ws, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ws;
  }

  private static Workspace workspace() {
    return workspace(UUID.randomUUID(), "Test Workspace", "Test Description");
  }

  private static WorkspaceMember member(UUID id, Workspace workspace, User user, WorkspaceRole role) {
    WorkspaceMember member = new WorkspaceMember(workspace, user, role);
    try {
      java.lang.reflect.Field field = WorkspaceMember.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(member, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return member;
  }

  @Nested
  @DisplayName("create")
  class Create {

@Test
    @DisplayName("Should create workspace and owner member when user exists")
    void create_Success() {
      UUID userId = UUID.randomUUID();
      UUID workspaceId = UUID.randomUUID();
      UUID memberId = UUID.randomUUID();
      CreateWorkspaceRequest request = new CreateWorkspaceRequest("New Workspace", "Description");
      User user = user(userId, UserRole.USER, UserStatus.ACTIVE);

      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> {
        Workspace ws = i.getArgument(0);
        try {
          java.lang.reflect.Field field = Workspace.class.getDeclaredField("id");
          field.setAccessible(true);
          field.set(ws, workspaceId);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return ws;
      });
      when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> {
        WorkspaceMember m = i.getArgument(0);
        try {
          java.lang.reflect.Field field = WorkspaceMember.class.getDeclaredField("id");
          field.setAccessible(true);
          field.set(m, memberId);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return m;
      });

      WorkspaceResponse response = workspaceService.create(request, userId);

      assertThat(response.name()).isEqualTo("New Workspace");
      assertThat(response.description()).isEqualTo("Description");
      assertThat(response.id()).isEqualTo(workspaceId);

      ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
      verify(workspaceRepository).save(workspaceCaptor.capture());
      assertThat(workspaceCaptor.getValue().getName()).isEqualTo("New Workspace");
      assertThat(workspaceCaptor.getValue().getDescription()).isEqualTo("Description");

      ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
      verify(workspaceMemberRepository).save(memberCaptor.capture());
      assertThat(memberCaptor.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
      assertThat(memberCaptor.getValue().getUser()).isEqualTo(user);
      assertThat(memberCaptor.getValue().getId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when user does not exist")
    void create_UserNotFound_Throws() {
      UUID userId = UUID.randomUUID();
      CreateWorkspaceRequest request = new CreateWorkspaceRequest("New Workspace", "Description");

      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.create(request, userId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("User not found");

      verify(userRepository).findById(userId);
      verify(workspaceRepository, never()).save(any());
      verify(workspaceMemberRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("findAll")
  class FindAll {

    @Test
    @DisplayName("Should return all workspaces mapped to response")
    void findAll_Success() {
      Workspace ws1 = workspace(UUID.randomUUID(), "Workspace 1", "Desc 1");
      Workspace ws2 = workspace(UUID.randomUUID(), "Workspace 2", "Desc 2");

      when(workspaceRepository.findAll()).thenReturn(List.of(ws1, ws2));

      List<WorkspaceResponse> responses = workspaceService.findAll();

      assertThat(responses).hasSize(2);
      assertThat(responses.get(0).name()).isEqualTo("Workspace 1");
      assertThat(responses.get(1).name()).isEqualTo("Workspace 2");

      verify(workspaceRepository).findAll();
    }

    @Test
    @DisplayName("Should return empty list when no workspaces exist")
    void findAll_Empty_ReturnsEmptyList() {
      when(workspaceRepository.findAll()).thenReturn(List.of());

      List<WorkspaceResponse> responses = workspaceService.findAll();

      assertThat(responses).isEmpty();
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    @DisplayName("Should return workspace when found")
    void findById_Success() {
      UUID id = UUID.randomUUID();
      Workspace workspace = workspace(id, "Workspace", "Desc");

      when(workspaceRepository.findById(id)).thenReturn(Optional.of(workspace));

      WorkspaceResponse response = workspaceService.findById(id);

      assertThat(response.id()).isEqualTo(id);
      assertThat(response.name()).isEqualTo("Workspace");
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when workspace does not exist")
    void findById_NotFound_Throws() {
      UUID id = UUID.randomUUID();

      when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.findById(id))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Workspace not found");
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("Should update workspace name and description")
    void update_Success() {
      UUID id = UUID.randomUUID();
      Workspace workspace = workspace(id, "Old Name", "Old Desc");
      UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("New Name", "New Desc");

      when(workspaceRepository.findById(id)).thenReturn(Optional.of(workspace));

      WorkspaceResponse response = workspaceService.update(id, request);

      assertThat(response.name()).isEqualTo("New Name");
      assertThat(response.description()).isEqualTo("New Desc");
      assertThat(workspace.getName()).isEqualTo("New Name");
      assertThat(workspace.getDescription()).isEqualTo("New Desc");
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when workspace does not exist")
    void update_NotFound_Throws() {
      UUID id = UUID.randomUUID();
      UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("New Name", "New Desc");

      when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.update(id, request))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Workspace not found");
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("Should delete workspace when found")
    void delete_Success() {
      UUID id = UUID.randomUUID();
      Workspace workspace = workspace(id, "Workspace", "Desc");

      when(workspaceRepository.findById(id)).thenReturn(Optional.of(workspace));

      workspaceService.delete(id);

      verify(workspaceRepository).delete(workspace);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when workspace does not exist")
    void delete_NotFound_Throws() {
      UUID id = UUID.randomUUID();

      when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.delete(id))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Workspace not found");

      verify(workspaceRepository, never()).delete(any());
    }
  }

  @Nested
  @DisplayName("findWorkspaceMembers")
  class FindWorkspaceMembers {

    @Test
    @DisplayName("Should return all members for workspace")
    void findWorkspaceMembers_Success() {
      UUID workspaceId = UUID.randomUUID();
      User user1 = user(UUID.randomUUID(), UserRole.USER, UserStatus.ACTIVE);
      User user2 = user(UUID.randomUUID(), UserRole.USER, UserStatus.ACTIVE);
      Workspace workspace = workspace(workspaceId, "Workspace", "Desc");
      WorkspaceMember member1 = member(UUID.randomUUID(), workspace, user1, WorkspaceRole.MEMBER);
      WorkspaceMember member2 = member(UUID.randomUUID(), workspace, user2, WorkspaceRole.ADMIN);

      when(workspaceMemberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(member1, member2));

      List<WorkspaceMemberResponse> responses = workspaceService.findWorkspaceMembers(
          new FindWorkspaceMembersRequest(), workspaceId);

      assertThat(responses).hasSize(2);
      assertThat(responses.get(0).role()).isEqualTo(WorkspaceRole.MEMBER);
      assertThat(responses.get(1).role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("Should return empty list when no members exist")
    void findWorkspaceMembers_Empty_ReturnsEmptyList() {
      UUID workspaceId = UUID.randomUUID();

      when(workspaceMemberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

      List<WorkspaceMemberResponse> responses = workspaceService.findWorkspaceMembers(
          new FindWorkspaceMembersRequest(), workspaceId);

      assertThat(responses).isEmpty();
    }
  }

  @Nested
  @DisplayName("addMember")
  class AddMember {

    @Test
    @DisplayName("Should add member when workspace and user exist")
    void addMember_Success() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("new@example.com", WorkspaceRole.MEMBER);
      Workspace workspace = workspace(workspaceId, "Workspace", "Desc");
      User user = user(userId, UserRole.USER, UserStatus.ACTIVE);

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
      when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
      when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

      WorkspaceMemberResponse response = workspaceService.addMember(request, workspaceId);

      assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);

      ArgumentCaptor<WorkspaceMember> captor = ArgumentCaptor.forClass(WorkspaceMember.class);
      verify(workspaceMemberRepository).save(captor.capture());
      assertThat(captor.getValue().getWorkspace()).isEqualTo(workspace);
      assertThat(captor.getValue().getUser()).isEqualTo(user);
      assertThat(captor.getValue().getRole()).isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    @DisplayName("Should throw FORBIDDEN when trying to add OWNER role")
    void addMember_OwnerRole_Throws() {
      UUID workspaceId = UUID.randomUUID();
      AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("new@example.com", WorkspaceRole.OWNER);

      assertThatThrownBy(() -> workspaceService.addMember(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Cannot grant owner permission");

      verify(workspaceRepository, never()).findById(any());
      verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when workspace does not exist")
    void addMember_WorkspaceNotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("new@example.com", WorkspaceRole.MEMBER);

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.addMember(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Workspace not found");
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when user does not exist")
    void addMember_UserNotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("new@example.com", WorkspaceRole.MEMBER);
      Workspace workspace = workspace(workspaceId, "Workspace", "Desc");

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
      when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.addMember(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("User not found");
    }
  }

  @Nested
  @DisplayName("updateMemberRole")
  class UpdateMemberRole {

    @Test
    @DisplayName("Should update member role when member exists")
    void updateMemberRole_Success() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("user@example.com", WorkspaceRole.ADMIN);
      Workspace workspace = workspace(workspaceId, "Workspace", "Desc");
      User user = user(userId, UserRole.USER, UserStatus.ACTIVE);
      WorkspaceMember member = member(UUID.randomUUID(), workspace, user, WorkspaceRole.MEMBER);

      when(workspaceMemberRepository.findByWorkspaceIdAndUserEmail(workspaceId, request.email()))
          .thenReturn(Optional.of(member));
      when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

      WorkspaceMemberResponse response = workspaceService.updateMemberRole(request, workspaceId);

      assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);
      assertThat(member.getRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("Should throw FORBIDDEN when trying to update to OWNER role")
    void updateMemberRole_OwnerRole_Throws() {
      UUID workspaceId = UUID.randomUUID();
      UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("user@example.com", WorkspaceRole.OWNER);

      assertThatThrownBy(() -> workspaceService.updateMemberRole(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Cannot grant owner permission");

      verify(workspaceMemberRepository, never()).findByWorkspaceIdAndUserEmail(any(), anyString());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when member does not exist")
    void updateMemberRole_NotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("user@example.com", WorkspaceRole.ADMIN);

      when(workspaceMemberRepository.findByWorkspaceIdAndUserEmail(workspaceId, request.email()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.updateMemberRole(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("User not found");
    }
  }

  @Nested
  @DisplayName("removeMember")
  class RemoveMember {

    @Test
    @DisplayName("Should remove member when member exists and is not owner")
    void removeMember_Success() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("user@example.com");
      Workspace workspace = workspace(workspaceId, "Workspace", "Desc");
      User user = user(userId, UserRole.USER, UserStatus.ACTIVE);
      WorkspaceMember member = member(UUID.randomUUID(), workspace, user, WorkspaceRole.MEMBER);

      when(workspaceMemberRepository.findByWorkspaceIdAndUserEmail(workspaceId, request.email()))
          .thenReturn(Optional.of(member));

      workspaceService.removeMember(request, workspaceId);

      verify(workspaceMemberRepository).delete(member);
    }

    @Test
    @DisplayName("Should throw FORBIDDEN when trying to remove owner")
    void removeMember_Owner_Throws() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("owner@example.com");
      Workspace workspace = workspace(workspaceId, "Workspace", "Desc");
      User user = user(userId, UserRole.USER, UserStatus.ACTIVE);
      WorkspaceMember owner = member(UUID.randomUUID(), workspace, user, WorkspaceRole.OWNER);

      when(workspaceMemberRepository.findByWorkspaceIdAndUserEmail(workspaceId, request.email()))
          .thenReturn(Optional.of(owner));

      assertThatThrownBy(() -> workspaceService.removeMember(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Cannot delete owner");

      verify(workspaceMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when member does not exist")
    void removeMember_NotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("user@example.com");

      when(workspaceMemberRepository.findByWorkspaceIdAndUserEmail(workspaceId, request.email()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> workspaceService.removeMember(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("User not found");

      verify(workspaceMemberRepository, never()).delete(any());
    }
  }
}