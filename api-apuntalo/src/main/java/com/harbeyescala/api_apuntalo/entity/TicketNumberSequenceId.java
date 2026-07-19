package com.harbeyescala.api_apuntalo.entity;
import jakarta.persistence.*; import lombok.*; import java.io.Serializable;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class TicketNumberSequenceId implements Serializable {
 @Column(name="negocio_id",nullable=false) private Long negocioId;
 @Column(name="store_id",nullable=false) private Long storeId;
}
