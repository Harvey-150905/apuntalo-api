package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.LoginRequestDto;
import com.harbeyescala.api_apuntalo.dto.LoginResponseDto;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponseDto login(LoginRequestDto dto) {
        User user = userRepository
                .findByUsernameAndNegocioId(dto.getUsername(), dto.getNegocioId())
                .orElseThrow(() -> new ResourceNotFoundException("Credenciales inválidas"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ResourceNotFoundException("Credenciales inválidas");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getNegocio().getId(),
                user.getNegocio().getNombre(),
                user.getRole().name()
        );

        return LoginResponseDto.builder()
                .token(token)
                .build();
    }
}