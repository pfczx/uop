package com.platform.uop.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private Authentication authentication;

  @InjectMocks
  private AuthService authService;

  @Nested
  @DisplayName("login")
  class Login {

    @Test
    @DisplayName("Should authenticate user using AuthenticationManager and return Authentication")
    void login_Success_DelegatesToAuthenticationManager() {
      LoginRequest request = new LoginRequest("user@example.com", "Password123!");
      when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
          .thenReturn(authentication);

      Authentication result = authService.login(request);

      assertThat(result).isSameAs(authentication);
      verify(authenticationManager).authenticate(
          new UsernamePasswordAuthenticationToken("user@example.com", "Password123!"));
    }

    @Test
    @DisplayName("Should propagate BadCredentialsException thrown by AuthenticationManager (wrong password)")
    void login_BadCredentials_Propagates() {
      LoginRequest request = new LoginRequest("user@example.com", "wrong");
      BadCredentialsException ex = new BadCredentialsException("Bad credentials");
      when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
          .thenThrow(ex);

      assertThatThrownBy(() -> authService.login(request))
          .isSameAs(ex);
    }

    @Test
    @DisplayName("Should propagate LockedException thrown by AuthenticationManager (locked account)")
    void login_Locked_Propagates() {
      LoginRequest request = new LoginRequest("locked@example.com", "Password123!");
      LockedException ex = new LockedException("Account locked");
      when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
          .thenThrow(ex);

      assertThatThrownBy(() -> authService.login(request))
          .isSameAs(ex);
    }

    @Test
    @DisplayName("Should propagate DisabledException thrown by AuthenticationManager (inactive account)")
    void login_Disabled_Propagates() {
      LoginRequest request = new LoginRequest("inactive@example.com", "Password123!");
      DisabledException ex = new DisabledException("Account disabled");
      when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
          .thenThrow(ex);

      assertThatThrownBy(() -> authService.login(request))
          .isSameAs(ex);
    }
  }
}
