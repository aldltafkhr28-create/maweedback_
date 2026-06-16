package com.example.demo;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() throws URISyntaxException {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            logger.info("DATABASE_URL not set - using local fallback (localhost:5432/maweed_db)");
            return DataSourceBuilder.create()
                    .url("jdbc:postgresql://localhost:5432/maweed_db")
                    .username("postgres")
                    .password("qwert12345")
                    .type(HikariDataSource.class)
                    .build();
        }

        logger.info("DATABASE_URL found - configuring datasource for Railway");
        URI dbUri = new URI(databaseUrl);
        String userInfo = dbUri.getUserInfo();
        String username = "";
        String password = "";
        if (userInfo != null && userInfo.contains(":")) {
            username = userInfo.split(":")[0];
            password = userInfo.split(":")[1];
        }
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();

        logger.info("Connecting to: {}", dbUrl);
        return DataSourceBuilder.create()
                .url(dbUrl)
                .username(username)
                .password(password)
                .type(HikariDataSource.class)
                .build();
    }
}
