package com.platform.uop.admin.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.platform.uop.admin.dto.AdminUserPageResponse;
import com.platform.uop.admin.dto.AdminUserResponse;
import com.platform.uop.admin.dto.ChangeRoleRequest;
import com.platform.uop.admin.dto.LockUserRequest;
import com.platform.uop.admin.dto.UnlockUserRequest;
import com.platform.uop.admin.exception.AdminUserNotFoundException;
import com.platform.uop.admin.exception.CannotModifyAdminException;
import com.platform.uop.admin.exception.UserAlreadyLockedException;
import com.platform.uop.admin.exception.UserNotLockedException;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

  private final UserRepository userRepository;

  private AdminUserResponse toResponse(User user) {
    return new AdminUserResponse(
        user.getId(),
        user.getEmail(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private User findUser(UUID id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new AdminUserNotFoundException(id));
  }

  private void validateNotAdmin(User user) {
    if (user.getRole() == UserRole.ADMIN) {
      throw new CannotModifyAdminException();
    }
  }

  public AdminUserResponse getUser(UUID id) {
    User user = findUser(id);
    return toResponse(user);
  }

  public AdminUserPageResponse getAllUsers(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<User> userPage = userRepository.findAll(pageable);

    List<AdminUserResponse> content = userPage.getContent().stream()
        .map(this::toResponse)
        .toList();

    return new AdminUserPageResponse(
        content,
        userPage.getNumber(),
        userPage.getSize(),
        userPage.getTotalElements(),
        userPage.getTotalPages(),
        userPage.isFirst(),
        userPage.isLast());
  }

  public AdminUserResponse lockUser(UUID id, LockUserRequest request) {
    User user = findUser(id);
    validateNotAdmin(user);

    if (user.getStatus() == UserStatus.LOCKED) {
      throw new UserAlreadyLockedException(id);
    }

    user.lock();
    User saved = userRepository.save(user);
    return toResponse(saved);
  }

  public AdminUserResponse unlockUser(UUID id, UnlockUserRequest request) {
    User user = findUser(id);
    validateNotAdmin(user);

    if (user.getStatus() != UserStatus.LOCKED) {
      throw new UserNotLockedException(id);
    }

    user.unlock();

    User saved = userRepository.save(user);
    return toResponse(saved);
  }

  public AdminUserResponse changeRole(UUID id, ChangeRoleRequest request) {
    User user = findUser(id);
    validateNotAdmin(user);

    if (request.role() == UserRole.ADMIN) {
      throw new CannotModifyAdminException();
    }

    user.changeRole(request.role());

    User saved = userRepository.save(user);
    return toResponse(saved);
  }

  public AdminUserResponse deactivateAccount(UUID id, DeactivateAccountRequest request) {
    User user = findUser(id);
    validateNotAdmin(user);

    user.deactivateAccount();
    User saved = userRepository.save(user);

    return toResponse(saved);
  }

}
