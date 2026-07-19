package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.LoginRequestDto;
import com.harbeyescala.api_apuntalo.dto.LoginResponseDto;
import com.harbeyescala.api_apuntalo.dto.MeResponseDto;
import com.harbeyescala.api_apuntalo.dto.SwitchStoreRequestDto;
import com.harbeyescala.api_apuntalo.dto.SwitchStoreResponseDto;
import com.harbeyescala.api_apuntalo.dto.UserStoreAccessResponseDto;
import com.harbeyescala.api_apuntalo.security.AuthenticatedUserPrincipal;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import com.harbeyescala.api_apuntalo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @PostMapping("/switch-store")
    public ResponseEntity<SwitchStoreResponseDto> switchStore(
            @Valid @RequestBody SwitchStoreRequestDto dto) {
        return ResponseEntity.ok(authService.switchStore(dto.getStoreId()));
    }

    @GetMapping("/stores")
    public ResponseEntity<java.util.List<UserStoreAccessResponseDto>> stores() {
        return ResponseEntity.ok(authService.authorizedStores());
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponseDto> me() {
        AuthenticatedUserPrincipal principal = currentUser.getPrincipal();

        return ResponseEntity.ok(
                MeResponseDto.builder()
                        .userId(principal.userId())
                        .username(principal.username())
                        .role(principal.role())
                        .tenant(MeResponseDto.TenantInfo.builder()
                                .id(principal.tenantId())
                                .name(principal.tenantName())
                                .build())
                        .activeStore(authService.currentActiveStore())
                        .defaultStoreId(authService.currentDefaultStoreId())
                        .build()
        );
    }
}
