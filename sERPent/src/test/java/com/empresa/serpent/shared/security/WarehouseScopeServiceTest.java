package com.empresa.serpent.shared.security;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Who may see which branch.
 *
 * <p>This is the single gate every scoped read goes through, so the cases below are the
 * whole of the rule: get one wrong and an employee reads another branch's numbers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Warehouse scope")
class WarehouseScopeServiceTest {

    private static final Long CENTRAL = 1L;
    private static final Long BRANCH = 2L;
    private static final Long SOMEONE_ELSES = 99L;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private WarehouseScopeService warehouseScopeService;

    @Nested
    @DisplayName("an admin")
    class Admin {

        @Test
        @DisplayName("Sees everything when no branch is named")
        void seesEverythingByDefault() {
            givenCurrentUser(UserRole.ADMIN, CENTRAL);

            WarehouseScope scope = warehouseScopeService.resolve(null);

            assertThat(scope.unrestricted()).isTrue();
            assertThat(scope.seesNothing()).isFalse();
        }

        @Test
        @DisplayName("Narrows to the branch they name, even one they are not assigned to")
        void narrowsToTheNamedBranch() {
            // An admin's own assignment governs where they may WRITE, not what they may read.
            givenCurrentUser(UserRole.ADMIN, CENTRAL);

            WarehouseScope scope = warehouseScopeService.resolve(SOMEONE_ELSES);

            assertThat(scope.unrestricted()).isFalse();
            assertThat(scope.warehouseIds()).containsExactly(SOMEONE_ELSES);
        }
    }

    @Nested
    @DisplayName("an employee")
    class Employee {

        @Test
        @DisplayName("Naming no branch means THEIR branches, not every branch")
        void defaultsToTheirOwnBranches() {
            // The case that matters most: if "no filter" meant "no restriction", omitting
            // the parameter would be a way straight around the whole thing.
            givenCurrentUser(UserRole.EMPLOYEE, CENTRAL, BRANCH);

            WarehouseScope scope = warehouseScopeService.resolve(null);

            assertThat(scope.unrestricted()).isFalse();
            assertThat(scope.warehouseIds()).containsExactlyInAnyOrder(CENTRAL, BRANCH);
        }

        @Test
        @DisplayName("May narrow to one of their own branches")
        void mayNarrowToTheirOwn() {
            givenCurrentUser(UserRole.EMPLOYEE, CENTRAL, BRANCH);

            WarehouseScope scope = warehouseScopeService.resolve(BRANCH);

            assertThat(scope.warehouseIds()).containsExactly(BRANCH);
        }

        @Test
        @DisplayName("Is refused a branch that is not theirs")
        void isRefusedSomeoneElsesBranch() {
            givenCurrentUser(UserRole.EMPLOYEE, CENTRAL);

            assertThatThrownBy(() -> warehouseScopeService.resolve(SOMEONE_ELSES))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No tenés permiso para ver los datos de ese depósito.");
        }

        @Test
        @DisplayName("With no branches assigned, sees nothing rather than everything")
        void withNoBranchesSeesNothing() {
            // The failure mode worth guarding: an empty assignment must not collapse into
            // "no restriction". Callers short-circuit on this instead of running a query
            // with an empty IN list.
            givenCurrentUser(UserRole.EMPLOYEE);

            WarehouseScope scope = warehouseScopeService.resolve(null);

            assertThat(scope.unrestricted()).isFalse();
            assertThat(scope.seesNothing()).isTrue();
        }
    }

    private void givenCurrentUser(UserRole role, Long... warehouseIds) {
        Set<WarehouseEntity> warehouses = new LinkedHashSet<>();
        for (Long id : warehouseIds) {
            warehouses.add(WarehouseEntity.builder().id(id).name("W" + id).active(true).build());
        }

        given(authenticatedUserService.requireCurrentUser()).willReturn(
                UserEntity.builder()
                        .id(10L)
                        .username("tester")
                        .role(role)
                        .warehouses(warehouses)
                        .build());
    }
}
