package com.empresa.serpent.cashcount;

import com.empresa.serpent.cashcount.domain.entity.CashCountEntity;
import com.empresa.serpent.cashcount.repository.CashCountRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.shared.security.JwtService;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
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
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may close which till, and whose closes they may read, over real HTTP.
 *
 * <p>An employee closes their own branch — that is the point of the feature — so this is not
 * about locking the endpoint down by role. It is about the branch: an employee must not
 * reach another branch's till, by naming it or by omitting it, and an admin must see all of
 * them.
 */
@SpringBootTest
@AutoConfigureMockMvc
// In-memory H2, like the other integration tests: never touches a real database.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
@DisplayName("Till counts over HTTP")
class CashCountAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private CashCountRepository cashCountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private WarehouseEntity central;
    private WarehouseEntity north;
    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        central = warehouseRepository.save(
                WarehouseEntity.builder().name("Central CCA").active(true).build());
        north = warehouseRepository.save(
                WarehouseEntity.builder().name("Norte CCA").active(true).build());

        paymentMethodRepository.save(PaymentMethodEntity.builder()
                .name("Efectivo CCA").isCash(true).active(true).build());

        // One stored close per branch, so the listing has something to hide or show.
        persistCount(central);
        persistCount(north);

        adminToken = tokenFor("admin_cca", UserRole.ADMIN, central);
        employeeToken = tokenFor("employee_cca", UserRole.EMPLOYEE, central);
    }

    @Nested
    @DisplayName("the expected amounts")
    class Expected {

        @Test
        @DisplayName("An employee may ask for their own branch")
        void ownBranchIsAllowed() throws Exception {
            mockMvc.perform(get("/api/cash-counts/expected")
                            .param("warehouseId", central.getId().toString())
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseName").value("Central CCA"));
        }

        @Test
        @DisplayName("An employee asking for another branch's till is refused")
        void foreignBranchIsRefused() throws Exception {
            mockMvc.perform(get("/api/cash-counts/expected")
                            .param("warehouseId", north.getId().toString())
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("An admin may ask for any branch, including ones they are not assigned to")
        void adminReachesEveryBranch() throws Exception {
            mockMvc.perform(get("/api/cash-counts/expected")
                            .param("warehouseId", north.getId().toString())
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseName").value("Norte CCA"));
        }
    }

    @Nested
    @DisplayName("closing the till")
    class Closing {

        @Test
        @DisplayName("An employee closes their own branch")
        void employeeClosesOwnBranch() throws Exception {
            mockMvc.perform(post("/api/cash-counts")
                            .header("Authorization", bearer(employeeToken))
                            .contentType("application/json")
                            .content("""
                                     {"warehouseId":%d,"openingFloat":1000,"countedAmounts":[]}
                                     """.formatted(central.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warehouseName").value("Central CCA"));
        }

        @Test
        @DisplayName("An employee may not close a branch they are not assigned to")
        void employeeCannotCloseForeignBranch() throws Exception {
            mockMvc.perform(post("/api/cash-counts")
                            .header("Authorization", bearer(employeeToken))
                            .contentType("application/json")
                            .content("""
                                     {"warehouseId":%d,"openingFloat":1000,"countedAmounts":[]}
                                     """.formatted(north.getId())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("the history")
    class History {

        @Test
        @DisplayName("Omitting the branch filter shows an employee only their own")
        void omittingTheFilterDoesNotLeak() throws Exception {
            // The way around a branch filter is not to use one, so "no filter" has to mean
            // "my branches" and not "everything".
            mockMvc.perform(get("/api/cash-counts").header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].warehouseName").value("Central CCA"));
        }

        @Test
        @DisplayName("An admin sees every branch's closes")
        void adminSeesEveryBranch() throws Exception {
            mockMvc.perform(get("/api/cash-counts").header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("The detail of another branch's close is refused, not just hidden from the list")
        void foreignDetailIsRefused() throws Exception {
            Long foreignId = cashCountRepository
                    .findFirstByWarehouseIdOrderByClosedAtDescIdDesc(north.getId())
                    .orElseThrow().getId();

            mockMvc.perform(get("/api/cash-counts/" + foreignId)
                            .header("Authorization", bearer(employeeToken)))
                    .andExpect(status().isForbidden());
        }
    }

    private void persistCount(WarehouseEntity warehouse) {
        UserEntity owner = userRepository.save(UserEntity.builder()
                .name("seed " + warehouse.getName())
                .username("seed_cca_" + warehouse.getId())
                .passwordHash("hash")
                .active(true)
                .role(UserRole.ADMIN)
                .build());

        cashCountRepository.save(CashCountEntity.builder()
                .warehouse(warehouse)
                .createdByUserEntity(owner)
                .closedAt(LocalDateTime.now().minusDays(1))
                .openingFloat(BigDecimal.ZERO)
                .unattributedAmount(BigDecimal.ZERO)
                .unattributedCount(0)
                .build());
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
