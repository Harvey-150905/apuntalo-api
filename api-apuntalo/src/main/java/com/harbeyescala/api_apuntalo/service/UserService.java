package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.UserRequestDto;
import com.harbeyescala.api_apuntalo.dto.UserResponseDto;
import com.harbeyescala.api_apuntalo.dto.UserUpdateDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final NegocioRepository negocioRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            NegocioRepository negocioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.negocioRepository = negocioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponseDto save(UserRequestDto dto) {
        Long negocioId = resolveNegocioId(dto.getNegocioId());

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("El negocio no existe"));

        if (userRepository.existsByUsernameAndNegocioId(dto.getUsername(), negocioId)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese username en este negocio");
        }

        User user = User.builder()
                .nombre(dto.getNombre())
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole())
                .negocio(negocio)
                .build();

        User savedUser = userRepository.save(user);

        return mapToResponseDto(savedUser);
    }

    public List<UserResponseDto> findAll() {
        if (isSuperAdmin()) {
            return userRepository.findAll()
                    .stream()
                    .map(this::mapToResponseDto)
                    .toList();
        }

        Long negocioId = SecurityUtils.getNegocioId();

        return userRepository.findByNegocioId(negocioId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public Optional<UserResponseDto> findById(Long id) {
        if (isSuperAdmin()) {
            return userRepository.findById(id)
                    .map(this::mapToResponseDto);
        }

        Long negocioId = SecurityUtils.getNegocioId();

        return userRepository.findByIdAndNegocioId(id, negocioId)
                .map(this::mapToResponseDto);
    }

    public void deleteById(Long id) {
        User user = getUserByScope(id);
        userRepository.delete(user);
    }

    public UserResponseDto update(Long id, UserUpdateDto dto) {
        User user = getUserByScope(id);

        Long negocioId = resolveNegocioId(dto.getNegocioId());

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("El negocio no existe"));

        if (userRepository.existsByUsernameAndNegocioIdAndIdNot(
                dto.getUsername(),
                negocioId,
                id
        )) {
            throw new DuplicateResourceException("Ya existe un usuario con ese username en este negocio");
        }

        user.setNombre(dto.getNombre());
        user.setUsername(dto.getUsername());
        user.setRole(dto.getRole());
        user.setNegocio(negocio);

        User updatedUser = userRepository.save(user);

        return mapToResponseDto(updatedUser);
    }

    private User getUserByScope(Long id) {
        if (isSuperAdmin()) {
            return userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        }

        Long negocioId = SecurityUtils.getNegocioId();

        return userRepository.findByIdAndNegocioId(id, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private Long resolveNegocioId(Long dtoNegocioId) {
        if (isSuperAdmin()) {
            if (dtoNegocioId == null) {
                throw new ResourceNotFoundException("El negocioId es obligatorio");
            }
            return dtoNegocioId;
        }

        return SecurityUtils.getNegocioId();
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(SecurityUtils.getRole());
    }

    private UserResponseDto mapToResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .username(user.getUsername())
                .role(user.getRole())
                .negocioId(user.getNegocio().getId())
                .negocioNombre(user.getNegocio().getNombre())
                .build();
    }
}