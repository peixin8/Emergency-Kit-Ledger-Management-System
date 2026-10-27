package com.emergency.ledger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    @Value("${app.admin.username:admin}") private String adminUsername;
    @Value("${app.admin.password:}") private String adminPassword;
    @Value("${app.admin.organization-name:默认组织}") private String organizationName;
    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, TokenService tokens) { this.jdbc = jdbc; this.encoder = encoder; this.tokens = tokens; }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        if (adminPassword.isBlank()) return;
        if (adminUsername.isBlank() || organizationName.isBlank()) throw new IllegalArgumentException("管理员用户名和组织名称不能为空");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
        if (count != null && count > 0) return;
        Long orgId = jdbc.query("SELECT id FROM organization WHERE name=?", rs -> rs.next() ? rs.getLong(1) : null, organizationName);
        if (orgId == null) {
            jdbc.update("INSERT INTO organization(name) VALUES (?)", organizationName);
            orgId = jdbc.queryForObject("SELECT id FROM organization WHERE name=?", Long.class, organizationName);
        }
        jdbc.update("INSERT INTO app_user(organization_id,username,password_hash,role,enabled) VALUES (?,?,?,?,?)", orgId, adminUsername, encoder.encode(adminPassword), "ADMIN", true);
    }
    public CurrentUser byId(long id) {
        return jdbc.query("SELECT id,username,role,organization_id FROM app_user WHERE id=? AND enabled=TRUE", rs -> rs.next() ? new CurrentUser(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4)) : null, id);
    }
    public LoginResult login(String username, String password) {
        CurrentUser user = jdbc.query("SELECT id,username,role,organization_id FROM app_user WHERE username=? AND enabled=TRUE", rs -> rs.next() ? new CurrentUser(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4)) : null, username);
        String hash = user == null ? null : jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, user.id());
        if (hash == null || !encoder.matches(password, hash)) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","用户名或密码错误");
        return new LoginResult(tokens.issue(user.id()), new UserView(user.idString(), user.username(), user.role(), user.organizationIdString()));
    }
    public record UserView(String id, String username, String role, String organizationId) {}
    public record LoginResult(String token, UserView user) {}
}
