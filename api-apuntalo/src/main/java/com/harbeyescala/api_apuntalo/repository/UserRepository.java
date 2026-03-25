package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndNegocioId(String username, Long negocioId);

    List<User> findByNegocioId(Long negocioId);

    Optional<User> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByUsernameAndNegocioId(String username, Long negocioId);

    boolean existsByUsernameAndNegocioIdAndIdNot(String username, Long negocioId, Long id);
}