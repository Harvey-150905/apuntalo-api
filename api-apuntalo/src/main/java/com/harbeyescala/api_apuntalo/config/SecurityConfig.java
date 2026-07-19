package com.harbeyescala.api_apuntalo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final AuditRequestContextFilter auditRequestContextFilter;
    private final ApiErrorWriter errorWriter;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:[*],http://127.0.0.1:[*],http://192.168.1.185:[*]}")
    private String allowedOriginPatterns;

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            AuditRequestContextFilter auditRequestContextFilter,
            ApiErrorWriter errorWriter
    ) {
        this.jwtFilter = jwtFilter;
        this.auditRequestContextFilter = auditRequestContextFilter;
        this.errorWriter = errorWriter;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException("Basic authentication is disabled"); };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) -> errorWriter.write(
                        request, response, 401, "UNAUTHORIZED", "Autenticación requerida"))
                .accessDeniedHandler((request, response, ex) -> errorWriter.write(
                        request, response, 403, "ACCESS_DENIED", "No tienes permisos para realizar esta acción")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/public/negocios").permitAll()
                .requestMatchers("/api/auth/me").authenticated()

                .requestMatchers("/api/admin/cash-management", "/api/admin/cash-management/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/admin/cash-reconciliation", "/api/admin/cash-reconciliation/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/admin/cash-registers", "/api/admin/cash-registers/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/cash-registers/active")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")
                .requestMatchers("/api/admin/cash-sessions", "/api/admin/cash-sessions/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/cash-sessions", "/api/cash-sessions/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.GET, "/api/users/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/users/**")
                    .hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/users/**")
                    .hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/**")
                    .hasRole("SUPER_ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/negocios/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/negocios/**")
                    .hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/negocios/**")
                    .hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/negocios/**")
                    .hasRole("SUPER_ADMIN")

                .requestMatchers("/api/subcategories/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/products/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")
                .requestMatchers("/api/products/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/mesas/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")
                .requestMatchers("/api/mesas/**").hasAnyRole("SUPER_ADMIN", "ADMIN")

                // TICKETS: primero rutas específicas
                // TICKETS: reportería visible para ADMIN / SUPER_ADMIN / CAMARERO (parcial)
                .requestMatchers("/api/tickets/paid/total")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers("/api/tickets/paid/payment-summary")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers("/api/tickets/paid/daily-summary")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers("/api/tickets/paid/average-ticket")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers("/api/tickets/paid/product-summary")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                // reportería sensible solo ADMIN / SUPER_ADMIN
                .requestMatchers("/api/tickets/paid/user-summary")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")

                .requestMatchers("/api/tickets/open")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")

                .requestMatchers("/api/tickets/cancelled")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")

                .requestMatchers("/api/tickets/cash-closing")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/tickets/mesa/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.POST, "/api/tickets")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.POST, "/api/tickets/*/lines")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.POST, "/api/tickets/*/pay")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/lines/*/cancel")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/lines/*/discount")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/batches/*/cancel")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/cancel")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                // al final la genérica
                .requestMatchers(HttpMethod.GET, "/api/tickets/{ticketId}")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                // AUDITORÍA (Fase 5.3): solo ADMIN / SUPER_ADMIN
                .requestMatchers(HttpMethod.GET, "/api/admin/audit-events")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            // Envuelve el resto de la cadena (incluido jwtFilter) para que el
            // requestId/idempotencyKey estén disponibles en toda la petición,
            // se autentique o no (Fase 5.3).
            .addFilterBefore(auditRequestContextFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("Idempotency-Replayed"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
