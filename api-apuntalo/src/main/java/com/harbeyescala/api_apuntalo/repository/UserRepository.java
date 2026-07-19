package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "negocio")
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long excludedUserId);

    List<User> findByNegocioId(Long negocioId);

    Optional<User> findByIdAndNegocioId(Long id, Long negocioId);

    @EntityGraph(attributePaths = {"negocio", "defaultStore"})
    Optional<User> findWithDefaultStoreByIdAndNegocioId(Long id, Long negocioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.negocio.id = :negocioId")
    Optional<User> findByIdAndNegocioIdForUpdate(@Param("id") Long id, @Param("negocioId") Long negocioId);
}
