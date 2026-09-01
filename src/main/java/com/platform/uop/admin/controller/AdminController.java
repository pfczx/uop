package com.platform.uop.admin.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.platform.uop.admin.dto.AdminUserPageResponse;
import com.platform.uop.admin.dto.AdminUserResponse;
import com.platform.uop.admin.dto.ChangeRoleRequest;
import com.platform.uop.admin.dto.LockUserRequest;
import com.platform.uop.admin.dto.UnlockUserRequest;
import com.platform.uop.admin.service.AdminService;
import com.platform.uop.users.dto.DeactivateAccountRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;

  @GetMapping("/{id}")
  public AdminUserResponse getUser(@PathVariable UUID id) {
    return adminService.getUser(id);
  }

  @GetMapping
  public AdminUserPageResponse getAllUsers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return adminService.getAllUsers(page, size);
  }

  @PatchMapping("/{id}/lock")
  public AdminUserResponse lockUser(
      @PathVariable UUID id,
      @Valid @RequestBody LockUserRequest request) {
    return adminService.lockUser(id, request);
  }

  @PatchMapping("/{id}/unlock")
  public AdminUserResponse unlockUser(
      @PathVariable UUID id,
      @Valid @RequestBody UnlockUserRequest request) {
    return adminService.unlockUser(id, request);
  }

  @PatchMapping("/{id}/role")
  public AdminUserResponse changeRole(
      @PathVariable UUID id,
      @Valid @RequestBody ChangeRoleRequest request) {
    return adminService.changeRole(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivateAccount(
      @PathVariable UUID id,
      @Valid @RequestBody DeactivateAccountRequest request) {
    adminService.deactivateAccount(id, request);
    return ResponseEntity.noContent().build();
  }
}
