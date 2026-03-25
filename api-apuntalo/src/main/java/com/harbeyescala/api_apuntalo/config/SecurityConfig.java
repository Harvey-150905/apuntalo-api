package com.harbeyescala.api_apuntalo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/public/negocios").permitAll()
                .requestMatchers("/api/auth/me").authenticated()

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
                .requestMatchers("/api/products/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
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

                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/batches/*/cancel")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/cancel")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                // al final la genérica
                .requestMatchers(HttpMethod.GET, "/api/tickets/{ticketId}")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN", "CAMARERO")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}