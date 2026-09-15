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

    /**
     * Lists users; inactive ones are excluded unless asked for. The search term matches
     * first name, last name or username.
     */
    // Los CAST no son decoracion, y van en CADA aparicion del parametro. Sin ellos, listar SIN
    // termino de busqueda manda el parametro a PostgreSQL como binario y la consulta revienta
    // con "no existe la funcion lower(bytea)". En H2 anda igual, que es por lo que no se vio
    // antes. El por que, y por que no alcanza con castear una sola: docs/OPTIONAL_FILTERS.md.
    @Query("""
           SELECT u FROM UserEntity u
           WHERE (CAST(:name AS String) IS NULL
                  OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%'))
                  OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%'))
                  OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
             AND (:includeInactive = TRUE OR u.active = TRUE)
           ORDER BY u.name
           """)
    List<UserEntity> search(@Param("name") String name, @Param("includeInactive") boolean includeInactive);

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