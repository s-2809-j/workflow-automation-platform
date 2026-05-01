package com.company.workflowautomation.shared.tenant;

import java.util.UUID;

public class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT_CONTEXT = new ThreadLocal<>();

    // Single setter — both method names now work, both delegate here
    public static void setTenantId(UUID tenantId) {
        TENANT_CONTEXT.set(tenantId);                    // ← was empty, now works
    }

    // Keep old name as alias so existing callers don't break
    public static void setTenantContext(UUID tenantId) {
        setTenantId(tenantId);                           // ← delegates to above
    }

    public static UUID getTenantId() {
        return TENANT_CONTEXT.get();
    }

    public static void clear() {
        TENANT_CONTEXT.remove();
    }
}