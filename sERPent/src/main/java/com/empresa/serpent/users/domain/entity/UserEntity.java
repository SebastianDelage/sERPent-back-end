package com.empresa.serpent.users.domain.entity;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.users.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "username", nullable = false, length = 80, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 150, unique = true)
    private String email;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * What this user may DO. Orthogonal to {@code warehouses}, which says WHERE.
     *
     * <p>Defaults to EMPLOYEE: a user created without an explicit role should get the
     * narrower one. The migration that introduced this column did the opposite for rows
     * that already existed, and for the opposite reason — see V24.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.EMPLOYEE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Warehouses this user may operate in. EAGER because every stock operation checks it
     * on the acting user, so a lazy set would just be a guaranteed second query (and a
     * LazyInitializationException risk outside a transaction).
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_warehouses",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id")
    )
    private Set<WarehouseEntity> warehouses = new LinkedHashSet<>();
}
