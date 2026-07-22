package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.UserRequestDto;
import com.harbeyescala.api_apuntalo.dto.UserResponseDto;
import com.harbeyescala.api_apuntalo.dto.UserUpdateDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final NegocioRepository negocioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final UserStoreAccessService userStoreAccessService;

    public UserService(
            UserRepository userRepository,
            NegocioRepository negocioRepository,
            PasswordEncoder passwordEncoder,
            CurrentUser currentUser,
            UserStoreAccessService userStoreAccessService
    ) {
        this.userRepository = userRepository;
        this.negocioRepository = negocioRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.userStoreAccessService = userStoreAccessService;
    }

    @Transactional
    public UserResponseDto save(UserRequestDto dto) {
        Long negocioId = resolveNegocioId(dto.getNegocioId());
        String normalizedUsername = AuthService.normalizeUsername(dto.getUsername());

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("El negocio no existe"));

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese username");
        }

        Store primaryStore = userStoreAccessService.getPrimaryStore(negocioId);

        User user = User.builder()
                .nombre(dto.getNombre())
                .username(normalizedUsername)
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole())
                .negocio(negocio)
                .defaultStore(primaryStore)
                .activo(true)
                .tokenVersion(1)
                .build();

        User savedUser = userRepository.saveAndFlush(user);
        User assignedBy = userRepository.findByIdAndNegocioId(currentUser.getUserId(), negocioId)
                .orElse(null);
        userStoreAccessService.createPrincipalAccessForNewUser(savedUser, primaryStore, assignedBy);

        return mapToResponseDto(savedUser);
    }

    public List<UserResponseDto> findAll() {
        if (currentUser.isSuperAdmin()) {
            return userRepository.findAll()
                    .stream()
                    .map(this::mapToResponseDto)
                    .toList();
        }

        Long negocioId = currentUser.getTenantId();

        return userRepository.findByNegocioId(negocioId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public Optional<UserResponseDto> findById(Long id) {
        if (currentUser.isSuperAdmin()) {
            return userRepository.findById(id)
                    .map(this::mapToResponseDto);
        }

        Long negocioId = currentUser.getTenantId();

        return userRepository.findByIdAndNegocioId(id, negocioId)
                .map(this::mapToResponseDto);
    }

    /**
     * @deprecated Fase 9 (F9.4): el borrado físico de usuarios queda
     * deshabilitado por conservación de historia. Usa la desactivación en
     * {@code PATCH /api/admin/users/{id}/status}. Se mantiene el endpoint por
     * compatibilidad, pero rechaza siempre la operación.
     */
    @Deprecated
    @Transactional
    public void deleteById(Long id) {
        // Se resuelve por scope para devolver 404 si el recurso no es visible.
        getUserByScope(id);
        throw new ConflictException(
                "USER_PHYSICAL_DELETE_DISABLED",
                "El borrado físico de usuarios está deshabilitado; usa la desactivación");
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto) {
        User user = getUserByScope(id);

        Long negocioId = resolveNegocioId(dto.getNegocioId());
        String normalizedUsername = AuthService.normalizeUsername(dto.getUsername());

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("El negocio no existe"));

        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, id)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese username");
        }

        boolean securitySensitiveChange = false;

        if (user.getRole() != dto.getRole()) {
            securitySensitiveChange = true;
        }

        if (!Objects.equals(user.getNegocio().getId(), negocio.getId())) {
            throw new ConflictException(
                    "USER_TENANT_CHANGE_NOT_SUPPORTED",
                    "No se puede cambiar el negocio de un usuario con accesos a tiendas");
        }

        boolean targetActivo = dto.getActivo() != null ? dto.getActivo() : user.getActivo();
        if (!Objects.equals(user.getActivo(), targetActivo)) {
            securitySensitiveChange = true;
        }

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            securitySensitiveChange = true;
        }

        user.setNombre(dto.getNombre());
        user.setUsername(normalizedUsername);
        user.setRole(dto.getRole());
        user.setActivo(targetActivo);

        if (securitySensitiveChange) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        User updatedUser = userRepository.save(user);

        return mapToResponseDto(updatedUser);
    }

    private User getUserByScope(Long id) {
        if (currentUser.isSuperAdmin()) {
            return userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        }

        Long negocioId = currentUser.getTenantId();

        return userRepository.findByIdAndNegocioId(id, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private Long resolveNegocioId(Long dtoNegocioId) {
        if (currentUser.isSuperAdmin()) {
            if (dtoNegocioId == null) {
                throw new ResourceNotFoundException("El negocioId es obligatorio");
            }
            return dtoNegocioId;
        }

        return currentUser.getTenantId();
    }

    private UserResponseDto mapToResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .username(user.getUsername())
                .role(user.getRole())
                .negocioId(user.getNegocio().getId())
                .negocioNombre(user.getNegocio().getNombre())
                .activo(user.getActivo())
                .build();
    }
}
