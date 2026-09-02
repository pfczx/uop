package com.platform.uop.unit.security;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.platform.uop.auth.security.UserDetailsServiceImpl;
import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private UserDetailsServiceImpl userDetailsService;

  @Nested
  @DisplayName("loadUserByUsername")
  class LoadUserByUsername {

    @Test
    @DisplayName("Should return UserPrincipal wrapping the exact User when found")
    void loadUserByUsername_Success_WrapsExactUser() {
      User user = User.builder()
          .email("test@example.com")
          .passwordHash("encoded_pwd")
          .role(UserRole.USER)
          .status(UserStatus.ACTIVE)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();
      when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

      UserDetails result = userDetailsService.loadUserByUsername("test@example.com");

      assertThat(result).isInstanceOf(UserPrincipal.class);
      assertThat(((UserPrincipal) result).getUser()).isSameAs(user);
      assertThat(result.getUsername()).isEqualTo("test@example.com");
      assertThat(result.getPassword()).isEqualTo("encoded_pwd");
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException with 'User not found' message when not found")
    void loadUserByUsername_NotFound_Throws() {
      when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent@example.com"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessage("User not found");

      verify(userRepository).findByEmail("nonexistent@example.com");
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException for empty username")
    void loadUserByUsername_EmptyUsername_Throws() {
      when(userRepository.findByEmail("")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userDetailsService.loadUserByUsername(""))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessage("User not found");
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException for blank username")
    void loadUserByUsername_BlankUsername_Throws() {
      when(userRepository.findByEmail("   ")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userDetailsService.loadUserByUsername("   "))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }
}
