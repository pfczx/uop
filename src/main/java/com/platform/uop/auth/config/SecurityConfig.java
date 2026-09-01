package com.platform.uop.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.platform.uop.auth.security.UserDetailsServiceImpl;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final UserDetailsServiceImpl userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final UserStatusFilter userStatusFilter;

  @Value("${app.security.csrf-enabled:true}")
  private boolean csrfEnabled;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    if (!csrfEnabled) {
      http.csrf(csrf -> csrf.disable());
    }

    http
        .formLogin(form -> form.disable())
        .securityContext(context -> context.requireExplicitSave(false))
        .httpBasic(httpBasic -> httpBasic.disable())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, authException) -> {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            }))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/setup/**").permitAll()
            .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
            .requestMatchers("/api/users").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/users/**").authenticated()
            .anyRequest().authenticated())
        .addFilterAfter(userStatusFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;

  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration configuration) throws Exception {

    return configuration.getAuthenticationManager();
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

}
