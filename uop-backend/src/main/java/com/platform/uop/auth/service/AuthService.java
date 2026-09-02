package com.platform.uop.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.platform.uop.auth.dto.LoginRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthenticationManager authenticationManager;

  public Authentication login(LoginRequest request) {

    UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
        request.email(),
        request.password());

    return authenticationManager.authenticate(token);
  }
}
