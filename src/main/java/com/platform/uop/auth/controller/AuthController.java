package com.platform.uop.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
import com.platform.uop.auth.dto.LogoutResponse;
import com.platform.uop.auth.dto.MeResponse;
import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final SecurityContextRepository securityContextRepository;

  @PostMapping("/login")
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {

    Authentication authentication = authService.login(request);

    SecurityContext context = SecurityContextHolder.createEmptyContext();

    context.setAuthentication(authentication);

    SecurityContextHolder.setContext(context);

    securityContextRepository.saveContext(
        context,
        httpRequest,
        httpResponse);

    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

    return new LoginResponse(
        principal.getUser().getId(),
        principal.getUser().getEmail(),
        principal.getUser().getRole());
  }

  @PostMapping("/logout")
  public LogoutResponse logout(
      Authentication authentication,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {

    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

    LogoutResponse response = new LogoutResponse(
        principal.getUser().getId(),
        principal.getUser().getEmail(),
        principal.getUser().getRole(),
        "User logged out successfully.");

    SecurityContextLogoutHandler handler = new SecurityContextLogoutHandler();
    handler.logout(httpRequest, httpResponse, authentication);

    return response;

  }

  @GetMapping("/me")
  public MeResponse me(
      Authentication authentication) {

    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

    MeResponse response = new MeResponse(
        principal.getUser().getId(),
        principal.getUser().getEmail(),
        principal.getUser().getRole(),
        principal.getUser().getStatus());

    return response;
  }

}
