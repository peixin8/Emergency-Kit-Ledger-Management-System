package com.emergency.ledger;

public record CurrentUser(long id, String username, String role, long organizationId) {
    public String idString() { return Long.toString(id); }
    public String organizationIdString() { return Long.toString(organizationId); }
}
