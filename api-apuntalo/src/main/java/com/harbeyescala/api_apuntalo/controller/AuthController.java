package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.LoginRequestDto;
import com.harbeyescala.api_apuntalo.dto.LoginResponseDto;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;
import com.harbeyescala.api_apuntalo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
        return ResponseEntity.ok(authService.login(dto));
    }
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        return ResponseEntity.ok(
            java.util.Map.of(
                "userId", SecurityUtils.getUserId(),
                "username", SecurityUtils.getUsername(),
                "negocioId", SecurityUtils.getNegocioId(),
                "negocioNombre", SecurityUtils.getNegocioNombre(),
                "role", SecurityUtils.getRole()
            )
        );
    }
    }