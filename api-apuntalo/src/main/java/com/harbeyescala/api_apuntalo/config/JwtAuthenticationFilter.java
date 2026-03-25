package com.harbeyescala.api_apuntalo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbeyescala.api_apuntalo.exception.ApiError;
import com.harbeyescala.api_apuntalo.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.extractClaims(token);
            String role = claims.get("role", String.class);

            var authorities = List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)
            );

            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    authorities
            );

            authentication.setDetails(claims);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), new ApiError("Token inválido o expirado"));
            return;
        }

        filterChain.doFilter(request, response);
    }
}