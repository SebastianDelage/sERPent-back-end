package com.serpent.serpent.transactions.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    private Long id;

    @Column(name = "name" ,nullable = false, length = 100)
    private String name;

    @Column(name = "last_name" ,nullable = false, length = 100)
    private String lastName;

    @Column(name = "username" ,nullable = false, length = 100)
    private String username;

    @Column(name = "password" ,nullable = false, length = 255)
    private String password;

    @Column(name = "email" ,nullable = false, length = 100)
    private String email;

    @Column(name = "active" ,nullable = false)
    private boolean active;

    @Column(name = "created_at" ,nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (!this.active) this.active = true;
    }
}
