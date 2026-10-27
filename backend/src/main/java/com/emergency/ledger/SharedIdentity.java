package com.emergency.ledger;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedIdentity {
    private static final String USERNAME = "__shared_ledger__";
    private final JdbcTemplate jdbc;

    public SharedIdentity(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public synchronized CurrentUser current() {
        List<CurrentUser> users = jdbc.query(
            "SELECT id, organization_id FROM app_user WHERE username=?",
            (rs, row) -> new CurrentUser(rs.getLong(1), "共享用户", "ADMIN", rs.getLong(2)), USERNAME);
        if (!users.isEmpty()) return users.get(0);

        List<Long> organizations = jdbc.query(
            "SELECT organization_id FROM emergency_device_ledger ORDER BY id LIMIT 1",
            (rs, row) -> rs.getLong(1));
        if (organizations.isEmpty()) organizations = jdbc.query(
            "SELECT id FROM organization ORDER BY id LIMIT 1", (rs, row) -> rs.getLong(1));
        long organizationId;
        if (organizations.isEmpty()) {
            jdbc.update("INSERT INTO organization(name) VALUES (?)", "共享台账");
            organizationId = jdbc.queryForObject("SELECT id FROM organization WHERE name=?", Long.class, "共享台账");
        } else organizationId = organizations.get(0);
        jdbc.update("INSERT INTO app_user(organization_id,username,password_hash,role,enabled) VALUES (?,?,?,?,?)",
            organizationId, USERNAME, "!disabled!", "ADMIN", false);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, USERNAME);
        return new CurrentUser(userId, "共享用户", "ADMIN", organizationId);
    }
}
