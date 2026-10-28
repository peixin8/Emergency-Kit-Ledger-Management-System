package com.emergency.ledger;

import com.mysql.cj.conf.ConnectionUrl;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads production files explicitly: src/test/resources/application.yml cannot mask them. */
class ProductionDataSourceConfigurationTest {
    private ApplicationContextRunner context(boolean dev) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(DataSourceTimeZoneConfiguration.class)
            .withInitializer(context -> {
                try {
                    var loader=new YamlPropertySourceLoader();
                    var sources=context.getEnvironment().getPropertySources();
                    var main=loader.load("production-main",new FileSystemResource("src/main/resources/application.yml")).get(0);
                    sources.addLast(main);
                    if(dev) sources.addBefore(main.getName(),loader.load("production-dev",
                        new FileSystemResource("src/main/resources/application-dev.yml")).get(0));
                } catch(IOException e) { throw new IllegalStateException(e); }
            });
    }

    @Test void defaultMysqlConfigurationAlignsDriverAndEveryPhysicalConnectionWithoutOpeningMysql() {
        context(false).run(context -> {
            assertThat(context).hasNotFailed();
            var source=context.getBean(HikariDataSource.class);
            assertThat(source.getConnectionInitSql()).isEqualTo("SET time_zone = '+08:00'");
            var properties=ConnectionUrl.getConnectionUrlInstance(source.getJdbcUrl(),source.getDataSourceProperties())
                .getConnectionArgumentsAsProperties();
            assertThat(properties.getProperty("connectionTimeZone")).isEqualTo("Asia/Shanghai");
            var binder=Binder.get(context.getEnvironment());
            var multipart=binder.bind("spring.servlet.multipart",MultipartProperties.class).get();
            assertThat(multipart.getMaxFileSize().toBytes()).isEqualTo(2097152);
            assertThat(multipart.getMaxRequestSize().toBytes()).isEqualTo(3145728);
            var server=binder.bind("server",ServerProperties.class).get();
            assertThat(server.getTomcat().getMaxSwallowSize().toBytes()).isEqualTo(-1);
        });
    }

    @Test void customMysqlUrlCannotChangeDriverTimestampInterpretation() {
        context(false).withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/test?connectionTimeZone=UTC")
            .run(context -> {
                var source=context.getBean(HikariDataSource.class);
                var properties=ConnectionUrl.getConnectionUrlInstance(source.getJdbcUrl(),source.getDataSourceProperties())
                    .getConnectionArgumentsAsProperties();
                assertThat(properties.getProperty("connectionTimeZone")).isEqualTo("Asia/Shanghai");
            });
    }

    @Test void devProductionConfigurationInitializesNewAndReplacementH2Connections() {
        context(true).withPropertyValues("spring.datasource.url=jdbc:h2:mem:production_config_test;MODE=MySQL",
            "spring.datasource.hikari.minimum-idle=0", "spring.datasource.hikari.maximum-pool-size=3")
            .run(context -> {
                assertThat(context).hasNotFailed();
                var source=context.getBean(HikariDataSource.class);
                try(var first=source.getConnection(); var second=source.getConnection()) {
                    assertShanghai(first); assertShanghai(second);
                }
                source.getHikariPoolMXBean().softEvictConnections();
                try(var replacement=source.getConnection()) { assertShanghai(replacement); }
            });
    }
    private void assertShanghai(java.sql.Connection connection) throws Exception {
        try(var statement=connection.createStatement(); var result=statement.executeQuery("SELECT CURRENT_TIMESTAMP")) {
            result.next();
            assertThat(result.getObject(1,OffsetDateTime.class).getOffset().getId()).isEqualTo("+08:00");
        }
    }
}
