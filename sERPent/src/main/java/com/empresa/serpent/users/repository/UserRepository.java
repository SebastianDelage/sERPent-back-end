package com.empresa.serpent.users.repository;

import com.empresa.serpent.users.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Lists users; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT u FROM UserEntity u
           WHERE (:includeInactive = TRUE OR u.active = TRUE)
           ORDER BY u.name
           """)
    List<UserEntity> search(@Param("includeInactive") boolean includeInactive);

    /**
     * Active users whose ONLY assigned warehouse is the given one — the users who would be
     * left unable to operate if it were deactivated. Counting the assignment rather than
     * checking membership is what makes "only" precise.
     */
    @Query("""
           SELECT u FROM UserEntity u
           JOIN u.warehouses w
           WHERE w.id = :warehouseId
             AND u.active = TRUE
             AND SIZE(u.warehouses) = 1
           ORDER BY u.name
           """)
    List<UserEntity> findActiveUsersWhoseOnlyWarehouseIs(@Param("warehouseId") Long warehouseId);
}