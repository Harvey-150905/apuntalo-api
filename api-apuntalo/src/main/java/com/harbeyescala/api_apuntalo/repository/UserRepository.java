package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "negocio")
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long excludedUserId);

    List<User> findByNegocioId(Long negocioId);

    Optional<User> findByIdAndNegocioId(Long id, Long negocioId);
}
