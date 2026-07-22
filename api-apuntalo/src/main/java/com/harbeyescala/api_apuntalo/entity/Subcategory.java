package com.harbeyescala.api_apuntalo.entity;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "subcategories",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"negocio_id", "store_id", "nombre", "category"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la subcategoría es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;

    @NotNull(message = "La categoría es obligatoria")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    /**
     * Fase 9 (F9.7): reemplaza el borrado físico. {@code false} equivale a
     * la subcategoría "eliminada" pero conserva su historia y las
     * referencias de productos existentes.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;
}
