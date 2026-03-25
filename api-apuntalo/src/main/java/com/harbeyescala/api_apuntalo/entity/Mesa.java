package com.harbeyescala.api_apuntalo.entity;

import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "mesas",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"negocio_id", "numero"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MesaStatus status = MesaStatus.FREE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;
}