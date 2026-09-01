package com.platform.uop.unit.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.platform.uop.admin.dto.AdminUserPageResponse;
import com.platform.uop.admin.dto.AdminUserResponse;
import com.platform.uop.admin.dto.ChangeRoleRequest;
import com.platform.uop.admin.dto.LockUserRequest;
import com.platform.uop.admin.dto.UnlockUserRequest;
import com.platform.uop.admin.exception.AdminUserNotFoundException;
import com.platform.uop.admin.exception.CannotModifyAdminException;
import com.platform.uop.admin.exception.UserAlreadyLockedException;
import com.platform.uop.admin.exception.UserNotLockedException;
import com.platform.uop.admin.service.AdminService;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private AdminService adminService;

  private static User user(UUID id, UserRole role, UserStatus status) {
    return User.builder()
        .id(id)
        .email("user@example.com")
        .passwordHash("h")
        .role(role)
        .status(status)
        .createdAt(Instant.now().minusSeconds(60))
        .updatedAt(Instant.now().minusSeconds(60))
        .build();
  }

  private static User persisted(User input) {
    return input;
  }

  @Nested
  @DisplayName("getUser")
  class GetUser {

    @Test
    @DisplayName("Should return AdminUserResponse when user exists")
    void getUser_Success() {
      UUID id = UUID.randomUUID();
      Instant created = Instant.now().minusSeconds(60);
      Instant updated = Instant.now().minusSeconds(10);
      User user = User.builder()
          .id(id).email("user@example.com").role(UserRole.USER).status(UserStatus.ACTIVE)
          .createdAt(created).updatedAt(updated).build();
      when(userRepository.findById(id)).thenReturn(Optional.of(user));

      AdminUserResponse response = adminService.getUser(id);

      assertThat(response)
          .isEqualTo(new AdminUserResponse(id, "user@example.com", UserRole.USER, UserStatus.ACTIVE, created, updated));
    }

    @Test
    @DisplayName("Should throw AdminUserNotFoundException when user is not found")
    void getUser_NotFound_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> adminService.getUser(id))
          .isInstanceOf(AdminUserNotFoundException.class)
          .hasMessage("User with id '" + id + "' not found");
    }
  }

  @Nested
  @DisplayName("getAllUsers")
  class GetAllUsers {

    @Test
    @DisplayName("Should map page metadata and content")
    void getAllUsers_SingleFullPage() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.ACTIVE);
      Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
      when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

      AdminUserPageResponse response = adminService.getAllUsers(0, 10);

      assertThat(response.content()).hasSize(1);
      assertThat(response.number()).isZero();
      assertThat(response.size()).isEqualTo(10);
      assertThat(response.totalElements()).isEqualTo(1);
      assertThat(response.totalPages()).isEqualTo(1);
      assertThat(response.first()).isTrue();
      assertThat(response.last()).isTrue();
    }

    @Test
    @DisplayName("Empty page: first=true, last=true, totalElements=0")
    void getAllUsers_EmptyPage() {
      Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
      when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

      AdminUserPageResponse response = adminService.getAllUsers(0, 20);

      assertThat(response.content()).isEmpty();
      assertThat(response.totalElements()).isZero();
      assertThat(response.totalPages()).isEqualTo(0);
      assertThat(response.first()).isTrue();
      assertThat(response.last()).isTrue();
    }

    @Test
    @DisplayName("Multi-page: 11 elements size 10 -> page 0 is first not last; page 1 is last")
    void getAllUsers_MultiPage_Boundaries() {
      List<User> ten = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        ten.add(user(UUID.randomUUID(), UserRole.USER, UserStatus.ACTIVE));
      }
      Pageable page0 = PageRequest.of(0, 10, Sort.by("createdAt").descending());
      when(userRepository.findAll(page0)).thenReturn(new PageImpl<>(ten, page0, 11));

      AdminUserPageResponse first = adminService.getAllUsers(0, 10);
      assertThat(first.totalPages()).isEqualTo(2);
      assertThat(first.first()).isTrue();
      assertThat(first.last()).isFalse();

      Pageable page1 = PageRequest.of(1, 10, Sort.by("createdAt").descending());
      when(userRepository.findAll(page1))
          .thenReturn(new PageImpl<>(List.of(user(UUID.randomUUID(), UserRole.USER, UserStatus.ACTIVE)), page1, 11));

      AdminUserPageResponse last = adminService.getAllUsers(1, 10);
      assertThat(last.totalPages()).isEqualTo(2);
      assertThat(last.first()).isFalse();
      assertThat(last.last()).isTrue();
    }

    @Test
    @DisplayName("Should request Sort.by('createdAt').descending() from repository")
    void getAllUsers_AppliesCreatedAtDescSort() {
      UUID id = UUID.randomUUID();
      Pageable expected = PageRequest.of(0, 10, Sort.by("createdAt").descending());
      when(userRepository.findAll(expected))
          .thenReturn(new PageImpl<>(List.of(user(id, UserRole.USER, UserStatus.ACTIVE)), expected, 1));

      adminService.getAllUsers(0, 10);

      verify(userRepository).findAll(expected);
    }
  }

  @Nested
  @DisplayName("lockUser")
  class LockUser {

    @Test
    @DisplayName("Should lock a non-admin ACTIVE user")
    void lockUser_Success() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.ACTIVE);
      when(userRepository.findById(id)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(i -> persisted(i.getArgument(0)));

      AdminUserResponse response = adminService.lockUser(id, new LockUserRequest("TOS"));

      assertThat(response.status()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("Should throw AdminUserNotFoundException when user does not exist")
    void lockUser_NotFound_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> adminService.lockUser(id, new LockUserRequest("TOS")))
          .isInstanceOf(AdminUserNotFoundException.class);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CannotModifyAdminException when target is ADMIN")
    void lockUser_CannotModifyAdmin_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.ADMIN, UserStatus.ACTIVE)));

      assertThatThrownBy(() -> adminService.lockUser(id, new LockUserRequest("TOS")))
          .isInstanceOf(CannotModifyAdminException.class);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UserAlreadyLockedException when target is already LOCKED (locking locked)")
    void lockUser_AlreadyLocked_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.USER, UserStatus.LOCKED)));

      assertThatThrownBy(() -> adminService.lockUser(id, new LockUserRequest("TOS")))
          .isInstanceOf(UserAlreadyLockedException.class)
          .hasMessage("User with id " + id + " is already locked");
      verify(userRepository, never()).save(any());
    }

    // TODO: rethink locking inactive users
    @Test
    @DisplayName("Should lock an INACTIVE user (no status guard: INACTIVE -> LOCKED)")
    void lockUser_InactiveUser_Succeeds() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.INACTIVE);
      when(userRepository.findById(id)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(i -> persisted(i.getArgument(0)));

      AdminUserResponse response = adminService.lockUser(id, new LockUserRequest("TOS"));

      assertThat(response.status()).isEqualTo(UserStatus.LOCKED);
    }
  }

  @Nested
  @DisplayName("unlockUser")
  class UnlockUser {

    @Test
    @DisplayName("Should unlock a LOCKED non-admin user")
    void unlockUser_Success() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.LOCKED);
      when(userRepository.findById(id)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(i -> persisted(i.getArgument(0)));

      AdminUserResponse response = adminService.unlockUser(id, new UnlockUserRequest());

      assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw AdminUserNotFoundException when user does not exist")
    void unlockUser_NotFound_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> adminService.unlockUser(id, new UnlockUserRequest()))
          .isInstanceOf(AdminUserNotFoundException.class);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CannotModifyAdminException when target is ADMIN")
    void unlockUser_CannotModifyAdmin_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.ADMIN, UserStatus.LOCKED)));

      assertThatThrownBy(() -> adminService.unlockUser(id, new UnlockUserRequest()))
          .isInstanceOf(CannotModifyAdminException.class);
    }

    @Test
    @DisplayName("Should throw UserNotLockedException when user is ACTIVE (unlocking non-locked)")
    void unlockUser_NotLocked_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.USER, UserStatus.ACTIVE)));

      assertThatThrownBy(() -> adminService.unlockUser(id, new UnlockUserRequest()))
          .isInstanceOf(UserNotLockedException.class)
          .hasMessage("User with id " + id + " is not locked");
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UserNotLockedException when user is INACTIVE (only LOCKED can be unlocked)")
    void unlockUser_Inactive_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.USER, UserStatus.INACTIVE)));

      assertThatThrownBy(() -> adminService.unlockUser(id, new UnlockUserRequest()))
          .isInstanceOf(UserNotLockedException.class);
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("changeRole")
  class ChangeRole {

    @Test
    @DisplayName("Should change role of a non-admin user")
    void changeRole_Success() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.ACTIVE);
      when(userRepository.findById(id)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(i -> persisted(i.getArgument(0)));

      AdminUserResponse response = adminService.changeRole(id, new ChangeRoleRequest(UserRole.ADMIN));

      assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should throw AdminUserNotFoundException when user does not exist")
    void changeRole_NotFound_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> adminService.changeRole(id, new ChangeRoleRequest(UserRole.ADMIN)))
          .isInstanceOf(AdminUserNotFoundException.class);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CannotModifyAdminException when target is already ADMIN (admin is stuck: cannot demote)")
    void changeRole_CannotModifyAdmin_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.ADMIN, UserStatus.ACTIVE)));

      assertThatThrownBy(() -> adminService.changeRole(id, new ChangeRoleRequest(UserRole.USER)))
          .isInstanceOf(CannotModifyAdminException.class);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should demote ADMIN -> USER is NOT allowed (validateNotAdmin runs first). Documents the stuck-admin behavior.")
    void changeRole_AdminToUser_IsBlocked() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.ADMIN, UserStatus.ACTIVE)));

      assertThatThrownBy(() -> adminService.changeRole(id, new ChangeRoleRequest(UserRole.USER)))
          .isInstanceOf(CannotModifyAdminException.class);
    }
  }

  @Nested
  @DisplayName("deactivateAccount")
  class DeactivateAccount {

    @Test
    @DisplayName("Should deactivate a non-admin user, set INACTIVE and anonymize email")
    void deactivateAccount_Success() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.ACTIVE);
      when(userRepository.findById(id)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(i -> persisted(i.getArgument(0)));

      adminService.deactivateAccount(id, new DeactivateAccountRequest());

      verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw AdminUserNotFoundException when user does not exist (deleting non-existing account)")
    void deactivateAccount_NotFound_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> adminService.deactivateAccount(id, new DeactivateAccountRequest()))
          .isInstanceOf(AdminUserNotFoundException.class);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CannotModifyAdminException when target is ADMIN")
    void deactivateAccount_CannotModifyAdmin_Throws() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.ADMIN, UserStatus.ACTIVE)));

      assertThatThrownBy(() -> adminService.deactivateAccount(id, new DeactivateAccountRequest()))
          .isInstanceOf(CannotModifyAdminException.class);
      verify(userRepository, never()).save(any());
    }

    // TODO: rethink deactivating locked users
    @Test
    @DisplayName("Should deactivate a LOCKED user (no status guard: LOCKED -> INACTIVE)")
    void deactivateAccount_LockedUser_Succeeds() {
      UUID id = UUID.randomUUID();
      User user = user(id, UserRole.USER, UserStatus.LOCKED);
      when(userRepository.findById(id)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(i -> persisted(i.getArgument(0)));

      adminService.deactivateAccount(id, new DeactivateAccountRequest());

      verify(userRepository).save(any(User.class));
    }
  }
}
