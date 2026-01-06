package org.opendroidpdf.app.fillsign;

/**
 * High-level Fill & Sign actions that translate into either a placement mode
 * (signature/initials) or a one-tap stamp.
 */
public enum FillSignAction {
    SIGNATURE,
    INITIALS,
    CHECKMARK,
    CROSS,
    DATE,
    NAME
}

