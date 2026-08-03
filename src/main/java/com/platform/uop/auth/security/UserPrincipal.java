package com.platform.uop.auth.security;

import java.util.Collection;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.platform.uop.users.entity.User;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {

  private final User user;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Set.of(
        new SimpleGrantedAuthority("ROLE_" + user.getRole()));
  }

  @Override
  public @Nullable String getPassword() {
    return this.user.getPasswordHash();
  }

  @Override
  public String getUsername() {
    return this.user.getEmail();
  }

}
