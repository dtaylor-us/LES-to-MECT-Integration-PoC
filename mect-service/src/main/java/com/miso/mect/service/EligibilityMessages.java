package com.miso.mect.service;

import com.miso.mect.domain.BlockingFlag;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * User-facing messages for withdrawal eligibility. MECT owns all wording so LES
 * can display the reason as-is without mapping codes or maintaining message text.
 */
public final class EligibilityMessages {

    private EligibilityMessages() {}

    /** Message when LMR has been withdrawn (no withdraw button, inform user). */
    public static final String WITHDRAWN = "This LMR has been withdrawn.";

    /** Message when LMR is not found in MECT (edge case). */
    public static final String LMR_NOT_FOUND = "This LMR is not available for withdrawal.";

    /** Message when withdrawal was already processed. */
    public static final String ALREADY_WITHDRAWN = "This LMR has already been withdrawn.";

    /**
     * User-facing message for each blocking flag. LES receives the combined reason
     * from MECT and displays it; no mapping of codes in LES.
     */
    public static String toUserMessage(BlockingFlag flag) {
        return switch (flag) {
            case ZRC_TRANSACTION_EXISTS ->
                    "A ZRC transaction exists for this LMR. Withdrawal is not allowed until it is resolved.";
            case HEDGE_REGISTRATION_SUBMITTED ->
                    "A hedge registration has been submitted for this LMR. Withdrawal is not allowed until it is resolved.";
            case OFFER_SUBMITTED ->
                    "An offer has been submitted for this LMR. Withdrawal is not allowed until it is resolved.";
            case FRAP_EXISTS ->
                    "A FRAP exists for this LMR. Withdrawal is not allowed until it is resolved.";
        };
    }

    /**
     * Build a single user-facing reason from the set of blocking flags.
     * Multiple reasons are joined with a space so the UI can show one message.
     */
    public static String reasonForFlags(Set<BlockingFlag> flags) {
        if (flags == null || flags.isEmpty()) return null;
        return flags.stream()
                .map(EligibilityMessages::toUserMessage)
                .collect(Collectors.joining(" "));
    }
}
