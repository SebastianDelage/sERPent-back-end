package com.empresa.serpent.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A registered point of sale bound to one warehouse.
 *
 * <p>An operation that names a terminal takes its warehouse from here instead of from the
 * request, so the operator never picks one. This is an operational convenience, not a
 * security control: the terminal id is an ordinary request field and a client can name
 * any terminal. The user's warehouse assignment is what actually constrains the
 * operation, and it is checked against the terminal's warehouse all the same.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "terminals")
public class TerminalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terminal_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
