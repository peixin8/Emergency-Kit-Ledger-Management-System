package com.emergency.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:bootstrap_recovery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.admin.password="
})
class BootstrapRecoveryTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;

    @Test void failedBootstrapRollsBackAndReusesExistingOrganization() {
        ReflectionTestUtils.setField(auth,"adminPassword","TestOnlyPassword123!");
        ReflectionTestUtils.setField(auth,"organizationName","恢复测试组织");
        ReflectionTestUtils.setField(auth,"adminUsername","x".repeat(81));
        assertThatThrownBy(auth::bootstrap).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization WHERE name=?",Integer.class,"恢复测试组织")).isZero();

        jdbc.update("INSERT INTO organization(name) VALUES (?)","恢复测试组织");
        ReflectionTestUtils.setField(auth,"adminUsername","recovery-admin");
        auth.bootstrap();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization WHERE name=?",Integer.class,"恢复测试组织")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?",Integer.class,"recovery-admin")).isEqualTo(1);
        auth.bootstrap();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class)).isEqualTo(1);
    }
}
