package com.empresa.serpent.transactions.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users",
        uniqueConstraints = {
        @UniqueConstraint(name="uk_users_username", columnNames="username"),
        @UniqueConstraint(name="uk_users_email", columnNames="email")
    }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
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
    private Boolean active;

    @Column(name = "created_at" ,nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}
