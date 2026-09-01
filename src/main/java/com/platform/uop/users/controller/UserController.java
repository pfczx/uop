package com.platform.uop.users.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.dto.UpdateEmailRequest;
import com.platform.uop.users.dto.UpdatePasswordRequest;
import com.platform.uop.users.dto.UserResponse;
import com.platform.uop.users.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return userService.create(request);
  }

  @PreAuthorize("#id == principal.user.id")
  @PatchMapping("/{id}/email")
  public UserResponse updateEmail(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateEmailRequest request) {
    return userService.updateEmail(id, request);
  }

  @PreAuthorize("#id == principal.user.id")
  @PatchMapping("/{id}/password")
  public UserResponse updatePassword(
      @PathVariable UUID id,
      @Valid @RequestBody UpdatePasswordRequest request) {
    return userService.updatePassword(id, request);
  }

  @PreAuthorize("#id == principal.user.id")
  @PatchMapping("/{id}/deactivate")
  public UserResponse deactivateAccount(
      @PathVariable UUID id,
      @Valid @RequestBody DeactivateAccountRequest request) {
    return userService.deactivateAccount(id, request);

  }
}
