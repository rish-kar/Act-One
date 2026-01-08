package com.prarambh.act.one.ticketing.support;

/**
 * Provides admin password for tests from environment or system property ACTONE_ADMIN_PASSWORD.
 */
public final class TestAdminPasswordProvider {
    private TestAdminPasswordProvider() {}

    public static String adminPassword() {
        String val = System.getProperty("ACTONE_ADMIN_PASSWORD");
        if (val == null || val.isEmpty()) {
            val = System.getenv("ACTONE_ADMIN_PASSWORD");
        }
        if (val == null || val.isEmpty()) {
            throw new IllegalStateException("ACTONE_ADMIN_PASSWORD must be set for tests");
        }
        return val;
    }
}
