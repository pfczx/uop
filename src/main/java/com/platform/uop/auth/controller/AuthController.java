package com.platform.uop.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
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
}
