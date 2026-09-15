package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseRequest;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.domain.enums.UserRole;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EL PRIMER DEPÓSITO DEL SISTEMA SE ASIGNA SOLO.
 *
 * <p>Sin esto, una instalación nueva queda inutilizable sin decir por qué. La migración V19
 * asigna depósitos a los usuarios existentes con un CROSS JOIN que corre UNA vez, al migrar;
 * sobre una base vacía no hay depósitos que asignar, así que no hace nada — correctamente. El
 * agujero está después: el primer depósito que alguien crea desde la app no se asigna a nadie,
 * y un usuario sin depósitos entra igual pero no puede vender. El fallo aparece recién cuando
 * se intenta la primera venta, lejos de su causa.
 *
 * <p>BASE PROPIA A PROPÓSITO. La regla es "no había NINGÚN depósito", o sea que depende del
 * estado global de la base. La base H2 del suite es compartida entre todas las clases de test y
 * varias crean depósitos, así que medir esto ahí daría un resultado que depende del orden en
 * que corren los tests. Con su propia base en memoria, la pregunta tiene una sola respuesta.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:first_warehouse_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@Transactional
class FirstWarehouseAssignmentTest {

    @Autowired WarehouseService warehouseService;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("El primer depósito del sistema se asigna a todos los usuarios")
    void assignsTheFirstWarehouseToEveryUser() {
        UserEntity admin = persistUser("admin", UserRole.ADMIN, true);
        UserEntity cajera = persistUser("cajera", UserRole.EMPLOYEE, true);
        UserEntity deBaja = persistUser("de_baja", UserRole.EMPLOYEE, false);

        assertThat(warehouseRepository.count()).isZero();

        warehouseService.create(new CreateWarehouseRequest("Depósito central", null));

        // Incluido el inactivo: si mañana lo reactivan, no queremos el mismo agujero de nuevo.
        assertThat(assignedWarehouseCount(admin)).isEqualTo(1);
        assertThat(assignedWarehouseCount(cajera)).isEqualTo(1);
        assertThat(assignedWarehouseCount(deBaja)).isEqualTo(1);
    }

    @Test
    @DisplayName("Con un depósito ya cargado, el siguiente no se asigna a nadie")
    void doesNotAssignWhenTheSystemAlreadyHasAWarehouse() {
        UserEntity cajera = persistUser("cajera", UserRole.EMPLOYEE, true);
        warehouseRepository.save(WarehouseEntity.builder().name("Depósito central").active(true).build());

        warehouseService.create(new CreateWarehouseRequest("Depósito norte", null));

        // Con varios depósitos, asignar automáticamente sería dar permisos que nadie pidió.
        assertThat(assignedWarehouseCount(cajera)).isZero();
    }

    @Test
    @DisplayName("Un primer depósito creado inactivo no se asigna a nadie")
    void doesNotAssignAWarehouseCreatedInactive() {
        UserEntity cajera = persistUser("cajera", UserRole.EMPLOYEE, true);

        warehouseService.create(new CreateWarehouseRequest("Depósito en obra", false));

        // Asignar uno inactivo contradice la regla de resolveRequiredWarehouses, que los rechaza
        // justamente para no dejar a alguien "bien configurado" y sin poder operar.
        assertThat(assignedWarehouseCount(cajera)).isZero();
    }

    private long assignedWarehouseCount(UserEntity user) {
        return userRepository.findById(user.getId()).orElseThrow().getWarehouses().size();
    }

    private UserEntity persistUser(String username, UserRole role, boolean active) {
        return userRepository.save(UserEntity.builder()
                .name(username)
                .username(username)
                .passwordHash("hash")
                .role(role)
                .active(active)
                .build());
    }
}
