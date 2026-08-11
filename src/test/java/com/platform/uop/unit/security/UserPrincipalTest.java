package com.platform.uop.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;

class UserPrincipalTest {

  @Nested
  @DisplayName("getAuthorities")
  class GetAuthorities {

    @Test
    @DisplayName("Should return ROLE_-prefixed authority matching user role")
    void getAuthorities_ReturnsRolePrefixedAuthority() {
      User user = User.builder().role(UserRole.ADMIN).build();

      assertThat(new UserPrincipal(user).getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should return unmodifiable authority set (Spring Security contract)")
    void getAuthorities_IsUnmodifiable() {
      User user = User.builder().role(UserRole.USER).build();

      assertThatThrownBy(() -> new UserPrincipal(user).getAuthorities().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

  }

  @Nested
  @DisplayName("isEnabled / isAccountNonLocked")
  class StatusFlags {

    @Test
    @DisplayName("isEnabled is true only for ACTIVE status")
    void isEnabled_OnlyActiveIsTrue() {
      assertThat(new UserPrincipal(User.builder().status(UserStatus.ACTIVE).build()).isEnabled()).isTrue();
      assertThat(new UserPrincipal(User.builder().status(UserStatus.INACTIVE).build()).isEnabled()).isFalse();
      assertThat(new UserPrincipal(User.builder().status(UserStatus.LOCKED).build()).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isAccountNonLocked is false only for LOCKED status")
    void isAccountNonLocked_OnlyLockedIsFalse() {
      assertThat(new UserPrincipal(User.builder().status(UserStatus.ACTIVE).build()).isAccountNonLocked()).isTrue();
      assertThat(new UserPrincipal(User.builder().status(UserStatus.INACTIVE).build()).isAccountNonLocked()).isTrue();
      assertThat(new UserPrincipal(User.builder().status(UserStatus.LOCKED).build()).isAccountNonLocked()).isFalse();
    }

  }

  @Nested
  @DisplayName("wired getters")
  class WiredGetters {

    @Test
    @DisplayName("getUsername delegates to user email, getPassword delegates to user passwordHash")
    void usernameAndPassword_DelegateToUser() {
      User user = User.builder()
          .email("admin@platform.com")
          .passwordHash("hashedPassword123")
          .build();

      UserPrincipal principal = new UserPrincipal(user);

      assertThat(principal.getUsername()).isEqualTo("admin@platform.com");
      assertThat(principal.getPassword()).isEqualTo("hashedPassword123");
      assertThat(principal.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("isAccountNonExpired / isCredentialsNonExpired are hardcoded true")
    void nonExpiredFlags_AreTrue() {
      UserPrincipal principal = new UserPrincipal(User.builder().status(UserStatus.ACTIVE).build());

      assertThat(principal.isAccountNonExpired()).isTrue();
      assertThat(principal.isCredentialsNonExpired()).isTrue();
    }
  }
}
