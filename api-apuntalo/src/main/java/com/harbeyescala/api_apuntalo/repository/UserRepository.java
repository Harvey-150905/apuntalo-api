package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Fase 9: bloqueo pesimista con default Store cargada para operaciones de asignación.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"negocio", "defaultStore"})
    @Query("select u from User u where u.id = :id and u.negocio.id = :negocioId")
    Optional<User> findWithDefaultStoreByIdAndNegocioIdForUpdate(
            @Param("id") Long id, @Param("negocioId") Long negocioId);

    /**
     * Listado administrativo paginado y filtrado (Fase 9, F9.4). Filtros
     * opcionales por estado, rol y texto (nombre o username).
     */
    @Query("""
            select u from User u
            where u.negocio.id = :negocioId
              and (:active is null or u.activo = :active)
              and (:role is null or u.role = :role)
              and (:q is null or lower(u.nombre) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.username) like lower(concat('%', cast(:q as string), '%')))
            order by u.nombre asc, u.id asc
            """)
    Page<User> searchAdmin(
            @Param("negocioId") Long negocioId,
            @Param("active") Boolean active,
            @Param("role") com.harbeyescala.api_apuntalo.entity.Role role,
            @Param("q") String q,
            Pageable pageable);

    boolean existsByDefaultStore_IdAndActivoTrue(Long storeId);
}
