package com.harbeyescala.api_apuntalo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stores",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stores_id_negocio", columnNames = {"id", "negocio_id"}),
                @UniqueConstraint(name = "uk_stores_negocio_normalized_name",
                        columnNames = {"negocio_id", "normalized_name"}),
                @UniqueConstraint(name = "uk_stores_negocio_code", columnNames = {"negocio_id", "code"})
        },
        indexes = @Index(name = "idx_stores_negocio_active_name",
                columnList = "negocio_id, active, name, id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;

    /**
     * Espejo interno de solo lectura para que Hibernate pueda resolver
     * {@code stores.negocio_id} como columna referenciable desde las FKs
     * compuestas. La asociación {@link #negocio} continúa siendo la única
     * propietaria y escribible de la columna.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "negocio_id", nullable = false, insertable = false, updatable = false)
    private Long negocioId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "primary_store", nullable = false)
    private Boolean primaryStore;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "cash_reconciliation_enabled", nullable = false)
    private Boolean cashReconciliationEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
