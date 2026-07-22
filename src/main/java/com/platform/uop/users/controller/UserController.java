package com.platform.uop.users.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.CreateUserResponse;
import com.platform.uop.users.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public CreateUserResponse create(
      @Valid @RequestBody CreateUserRequest request) {
    return userService.create(request);
  }
}
