package com.klingai.poc.hello.klingmcp.auth;

import java.util.List;

public record OwnerIdentity(
        String subject,
        String clientId,
        String organizationId,
        List<String> authorities) {

    public boolean canManageAllTasks() {
        return authorities != null && authorities.contains("SCOPE_kling:video:admin");
    }
}
