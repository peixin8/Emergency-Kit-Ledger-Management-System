package com.emergency.ledger;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class DataSourceTimeZoneConfiguration {
    @Bean
    static BeanPostProcessor mysqlConnectionTimeZone() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean,String name) {
                if(bean instanceof HikariDataSource source && source.getJdbcUrl()!=null
                    && source.getJdbcUrl().startsWith("jdbc:mysql:")) {
                    // 即使部署方覆盖 DB_URL，驱动仍按与会话一致的上海时间解释 TIMESTAMP。
                    source.addDataSourceProperty("connectionTimeZone","Asia/Shanghai");
                }
                return bean;
            }
        };
    }
}
