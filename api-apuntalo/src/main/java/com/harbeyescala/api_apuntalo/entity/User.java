package com.harbeyescala.api_apuntalo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = {"username"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(nullable = false)
    private String nombre;

    /**
     * Username único globalmente en toda la plataforma. Se almacena siempre
     * normalizado (trim + minúsculas) para que la restricción única de BD
     * baste como defensa adicional a las consultas IgnoreCase.
     */
    @NotBlank(message = "El username es obligatorio")
    @Column(nullable = false)
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotNull(message = "El rol es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @NotNull(message = "El negocio es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;

    @NotNull(message = "La tienda predeterminada es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_store_id", nullable = false)
    private Store defaultStore;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    /**
     * Se incrementa en cada evento que invalida tokens ya emitidos
     * (cambio de password, rol, negocio o desactivación). El filtro JWT
     * rechaza cualquier token cuyo tokenVersion no coincida con el actual.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer tokenVersion = 1;
}
