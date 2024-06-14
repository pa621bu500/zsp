package com.pabu5h.zsp.config;

import org.pabuff.oqghelper.OqgHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DbConfig {
    @Bean(name = "pub3zspDataSource")
    @ConfigurationProperties(prefix = "cloud.aws.rds.dev-db")
    public DataSource dataSourceHarvWrite() {
        return new DriverManagerDataSource();
    }

    @Bean(name = "jdbcTemplatePub3")
    public JdbcTemplate jdbcTemplatePub3(@Qualifier("pub3zspDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
