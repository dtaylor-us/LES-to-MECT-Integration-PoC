package org.misoenergy.les.domain;

/**
 * Lifecycle status of an LMR enrollment in LES.
 * WITHDRAWN_REQUESTED: user requested withdrawal; awaiting MECT result.
 */
public enum EnrollmentStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    WITHDRAWN_REQUESTED,
    WITHDRAWN,
    WITHDRAW_REJECTED
}
