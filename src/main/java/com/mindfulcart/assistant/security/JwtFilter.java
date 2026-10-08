package com.mindfulcart.assistant.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null
        && authorization.startsWith("Bearer ")
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      String token = authorization.substring(7);
      try {
        UserDetails user = userDetailsService.loadUserByUsername(jwtService.username(token));
        if (jwtService.valid(token, user)) {
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        }
      } catch (RuntimeException ignored) {
        // Invalid or expired tokens continue as anonymous and are rejected by Spring Security.
      }
    }
    filterChain.doFilter(request, response);
  }
}
