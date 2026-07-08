package com.marketmono.entity;

/**
 * Source type codes for bookmark references.
 * Stored as VARCHAR(10) — no PostgreSQL enum type needed.
 */
public enum BookmarkSourceType {
    FA("Flow Alert"),
    OT("Option Trade");

    private final String displayName;

    BookmarkSourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
