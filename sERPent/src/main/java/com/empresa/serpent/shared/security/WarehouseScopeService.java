package com.empresa.serpent.shared.security;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.domain.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Which branches the caller is allowed to SEE.
 *
 * <p>Distinct from {@code WarehouseAccessService}, which answers where they may WRITE. This
 * one governs reads, and it exists because hiding a branch in the UI while leaving the
 * endpoint open is not a restriction at all: an employee could still ask for another
 * branch's stock by editing the URL.
 *
 * <p>Every query that can return per-branch data funnels through here, so the rule lives in
 * one place: an ADMIN sees everything, an EMPLOYEE sees only their assigned warehouses, and
 * naming a branch they are not assigned to is refused rather than quietly ignored.
 */
@Service
@RequiredArgsConstructor
public class WarehouseScopeService {

    private final AuthenticatedUserService authenticatedUserService;

    /**
     * What the caller may see, given the branch they asked for (possibly none).
     *
     * <ul>
     *   <li>ADMIN with no branch → everything.
     *   <li>ADMIN naming a branch → that branch.
     *   <li>EMPLOYEE with no branch → their own branches, NOT everything. This is the case
     *       that matters: leaving it unrestricted would turn "no filter" into a way around
     *       the whole thing.
     *   <li>EMPLOYEE naming a branch they have → that branch.
     *   <li>EMPLOYEE naming a branch they do not have → refused.
     * </ul>
     */
    @Transactional(readOnly = true)
    public WarehouseScope resolve(Long requestedWarehouseId) {
        UserEntity user = authenticatedUserService.requireCurrentUser();

        if (user.getRole() == UserRole.ADMIN) {
            return requestedWarehouseId == null
                    ? WarehouseScope.all()
                    : WarehouseScope.limitedTo(List.of(requestedWarehouseId));
        }

        List<Long> assigned = user.getWarehouses().stream()
                .map(WarehouseEntity::getId)
                .toList();

        if (requestedWarehouseId == null) {
            return WarehouseScope.limitedTo(assigned);
        }

        if (!assigned.contains(requestedWarehouseId)) {
            throw new ForbiddenException("No tenés permiso para ver los datos de ese depósito.");
        }

        return WarehouseScope.limitedTo(List.of(requestedWarehouseId));
    }

    /** True when the caller may see every branch. Used where a scope object is overkill. */
    @Transactional(readOnly = true)
    public boolean callerSeesEverything() {
        return authenticatedUserService.requireCurrentUser().getRole() == UserRole.ADMIN;
    }

    /**
     * The branches a query must be limited to.
     *
     * @param unrestricted  when true, no branch restriction applies and {@code warehouseIds}
     *                      is meaningless
     * @param warehouseIds  the branches the caller may see; EMPTY means they may see none,
     *                      which callers must short-circuit rather than pass to a query —
     *                      an empty IN list is a portability trap, and the answer is known
     *                      without asking the database anyway
     */
    public record WarehouseScope(boolean unrestricted, List<Long> warehouseIds) {

        static WarehouseScope all() {
            return new WarehouseScope(true, List.of());
        }

        static WarehouseScope limitedTo(List<Long> warehouseIds) {
            return new WarehouseScope(false, List.copyOf(warehouseIds));
        }

        /** The caller may see nothing at all: every scoped query returns empty. */
        public boolean seesNothing() {
            return !unrestricted && warehouseIds.isEmpty();
        }
    }
}
