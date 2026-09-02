package com.platform.uop.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;

import java.io.IOException;

@Component
public class UserStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserStatusFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {

            User user = userRepository.findById(principal.getUser().getId()).orElse(null);

            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User account is not active");
                return;
            }

            if (!principal.getUser().getEmail().equals(user.getEmail())) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User account has been modified");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}