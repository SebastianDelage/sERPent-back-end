package com.empresa.serpent.shared.security;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.domain.enums.UserRole;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The restrictions, exercised over HTTP with real tokens.
 *
 * <p>Deliberately end-to-end rather than unit-level. Hiding a button and leaving the
 * endpoint open is not a restriction, so what matters is what the SERVER answers when an
 * employee asks directly — with a URL they typed themselves, not one the UI offered them.
 *
 * <p>Fixture: two branches, one product with stock in each. The admin is assigned to
 * Central only, to prove reading is governed by the ROLE and not by the assignment; the
 * employee is assigned to Central only, so Norte is somebody else's branch.
 */
@SpringBootTest
@AutoConfigureMockMvc
// In-memory H2, like the other integration tests: never touches a real database.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
@DisplayName("Role restrictions over HTTP")
class RoleRestrictionsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryStockSnapshotRepository snapshotRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private WarehouseEntity central;
    private WarehouseEntity norte;
    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        central = warehouseRepository.save(
                WarehouseEntity.builder().name("Central RRIT").active(true).build());
        norte = warehouseRepository.save(
                WarehouseEntity.builder().name("Norte RRIT").active(true).build());

        ProductEntity product = productRepository.save(ProductEntity.builder()
                .name("Pollo RRIT").description("Pollo").price(new BigDecimal("1000.0000"))
                .sku("POLLO_RRIT").active(true).build());

        snapshotRepository.save(InventoryStockSnapshotEntity.builder()
                .product(product).warehouse(central)
                .currentStock(new BigDecimal("10.000")).build());
        snapshotRepository.save(InventoryStockSnapshotEntity.builder()
                .product(product).warehouse(norte)
                .currentStock(new BigDecimal("77.000")).build());

        adminToken = tokenFor("admin_rrit", UserRole.ADMIN, central);
        employeeToken = tokenFor("employee_rrit", UserRole.EMPLOYEE, central);
    }

    @Nested
    @DisplayName("the catalog")
    class Catalog {

        @Test
        @DisplayName("An employee may read it")
        void employeeMayRead() throws Exception {
            // Not hidden: an employee who cannot look up a price has a broken app.
            mockMvc.perform(get("/api/products").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("An employee may not create, edit or reprice")
        void employeeMayNotWrite() throws Exception {
            mockMvc.perform(post("/api/products")
                            .header("Authorization", bearer(employeeToken))
                            .contentType("application/json")
                            .content("""
                                     {"name":"Nuevo","price":100,"unitOfMeasure":"UNIT"}
                                     """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("An admin may write")
        void adminMayWrite() throws Exception {
            mockMvc.perform(post("/api/products")
                            .header("Authorization", bearer(adminToken))
                            .contentType("application/json")
                            .content("""
                                     {"name":"Nuevo RRIT","price":100,"unitOfMeasure":"UNIT"}
                                     """))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("admin-only areas")
    class AdminOnly {

        @Test
        @DisplayName("Users and supplier payments are closed to an employee")
        void closedToEmployee() throws Exception {
            for (String url : new String[]{"/api/users", "/api/supplier-payments"}) {
                mockMvc.perform(get(url).header("Authorization", bearer(employeeToken)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("An employee may read terminals but not create one")
        void terminalsReadOnlyForEmployee() throws Exception {
            // The topbar warehouse/terminal selector needs this for every authenticated
            // user, so only creating/editing terminals is ADMIN-only, not reading them.
            mockMvc.perform(get("/api/terminals").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/terminals")
                            .header("Authorization", bearer(employeeToken))
                            .contentType("application/json")
                            .content("""
                                     {"name":"Caja RRIT","warehouseId":%d}
                                     """.formatted(central.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Consolidated reports are closed to an employee")
        void consolidatedReportsClosed() throws Exception {
            // Consolidated BY CONSTRUCTION: no branch parameter to scope them by, so what
            // they answer is always every branch at once.
            for (String url : new String[]{
                    "/api/reports/inventory/by-warehouse",
                    "/api/reports/inventory/warehouse-summary",
                    "/api/reports/inventory/movements/by-warehouse"}) {
                mockMvc.perform(get(url).header("Authorization", bearer(employeeToken)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Replenishment is open to an employee now that it scopes by branch")
        void replenishmentIsOpenAndScoped() throws Exception {
            /*
             It used to be in the list above, and its @PreAuthorize said why: ADMIN only for
             lack of a branch filter, not because the data was sensitive. It has one now, so
             the restriction went away — an employee needs to know what to reorder where they
             work. What replaces it is the ordinary branch scoping: their own branches only.
            */
            mockMvc.perform(get("/api/reports/inventory/replenishment")
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk());

            // And naming somebody else's branch is still refused, filter or no filter.
            mockMvc.perform(get("/api/reports/inventory/replenishment")
                            .param("warehouseId", norte.getId().toString())
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("An employee still reaches their own branch list, for the session selector")
        void ownWarehousesStayReachable() throws Exception {
            mockMvc.perform(get("/api/users/me/warehouses")
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Central RRIT"));
        }
    }

    @Nested
    @DisplayName("branch data an employee is not assigned to")
    class ForeignBranchData {

        @Test
        @DisplayName("Asking for it outright is refused")
        void namingItIsRefused() throws Exception {
            mockMvc.perform(get("/api/stock")
                            .param("warehouseId", norte.getId().toString())
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Omitting the filter does not hand it over either")
        void omittingTheFilterDoesNotLeakIt() throws Exception {
            // The way around a branch filter is not to use one, so "no filter" has to mean
            // "my branches" and not "everything".
            mockMvc.perform(get("/api/stock").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].warehouseName").value("Central RRIT"));
        }

        @Test
        @DisplayName("The paginated stock screen is scoped the same way")
        void paginatedStockIsScoped() throws Exception {
            mockMvc.perform(get("/api/stock/search").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("So are the per-warehouse and per-product lookups")
        void otherStockRoutesAreScoped() throws Exception {
            mockMvc.perform(get("/api/stock/warehouse/" + norte.getId())
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isForbidden());

            // The cross-branch total must not become a way to read the other branch's number.
            mockMvc.perform(get("/api/stock/products").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].totalStock").value(10.0));
        }

        @Test
        @DisplayName("An admin sees both branches")
        void adminSeesEverything() throws Exception {
            // Same request, different role: the admin's own assignment is Central only, and
            // it does not narrow what they may read.
            mockMvc.perform(get("/api/stock").header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("a token whose user was deactivated")
    class DeactivatedUser {

        @Test
        @DisplayName("Stops working immediately, without waiting for the token to expire")
        void stopsWorkingAtOnce() throws Exception {
            UserEntity employee = userRepository.findByUsername("employee_rrit").orElseThrow();
            employee.setActive(false);
            userRepository.saveAndFlush(employee);

            // The token is still perfectly valid and unexpired; the account is not.
            mockMvc.perform(get("/api/stock").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String tokenFor(String username, UserRole role, WarehouseEntity... warehouses) {
        Set<WarehouseEntity> assigned = new LinkedHashSet<>(Set.of(warehouses));

        UserEntity user = userRepository.save(UserEntity.builder()
                .name(username)
                .username(username)
                .passwordHash(passwordEncoder.encode("secret123"))
                .active(true)
                .role(role)
                .warehouses(assigned)
                .build());

        return jwtService.generateToken(user.getUsername(), user.getId());
    }
}
