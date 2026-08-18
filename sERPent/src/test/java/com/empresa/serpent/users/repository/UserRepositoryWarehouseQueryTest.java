package com.empresa.serpent.users.repository;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the SIZE()-based JPQL behind the "you would strand these users" guard, which a
 * mocked repository could never prove actually runs.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryWarehouseQueryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository repository;

    private WarehouseEntity persistWarehouse(String name) {
        return entityManager.persistAndFlush(
                WarehouseEntity.builder().name(name).active(true).build());
    }

    private UserEntity persistUser(String username, boolean active, WarehouseEntity... warehouses) {
        return entityManager.persistAndFlush(UserEntity.builder()
                .name(username)
                .username(username)
                .passwordHash("hash")
                .active(active)
                .warehouses(new LinkedHashSet<>(Set.of(warehouses)))
                .build());
    }

    @Test
    @DisplayName("Finds only the active users whose single warehouse is the given one")
    void findsOnlyUsersStrandedByTheDeactivation() {
        WarehouseEntity central = persistWarehouse("Central");
        WarehouseEntity north = persistWarehouse("Sucursal Norte");

        UserEntity onlyCentral = persistUser("solo_central", true, central);
        persistUser("ambos", true, central, north);          // has a fallback
        persistUser("solo_norte", true, north);              // unaffected
        persistUser("inactivo_central", false, central);     // inactive: cannot operate anyway

        List<UserEntity> stranded = repository.findActiveUsersWhoseOnlyWarehouseIs(central.getId());

        assertThat(stranded)
                .extracting(UserEntity::getUsername)
                .containsExactly(onlyCentral.getUsername());
    }

    @Test
    @DisplayName("Returns empty when every assigned user has another warehouse to fall back on")
    void returnsEmptyWhenNobodyIsStranded() {
        WarehouseEntity central = persistWarehouse("Central");
        WarehouseEntity north = persistWarehouse("Sucursal Norte");

        persistUser("ambos", true, central, north);

        assertThat(repository.findActiveUsersWhoseOnlyWarehouseIs(central.getId())).isEmpty();
    }
}
