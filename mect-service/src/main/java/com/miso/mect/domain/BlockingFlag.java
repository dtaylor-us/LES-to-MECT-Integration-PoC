package com.miso.mect.domain;

/**
 * Flags that block withdrawal in MECT (authoritative).
 */
public enum BlockingFlag {
    ZRC_TRANSACTION_EXISTS,
    HEDGE_REGISTRATION_SUBMITTED,
    OFFER_SUBMITTED,
    FRAP_EXISTS
}
