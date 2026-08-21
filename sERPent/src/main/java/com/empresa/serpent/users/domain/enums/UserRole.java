package com.empresa.serpent.users.domain.enums;

/**
 * What a user is allowed to DO. Orthogonal to the user's warehouse assignment, which says
 * WHERE they may register operations: a role does not widen the branches you can act in,
 * and an assignment does not grant you the catalog.
 */
public enum UserRole {

    /** The owner: everything, including the catalog, users, terminals and consolidated reports. */
    ADMIN,

    /**
     * Staff: operates and consults, but reads the catalog without editing it, and only ever
     * sees the branches assigned to them.
     */
    EMPLOYEE
}
