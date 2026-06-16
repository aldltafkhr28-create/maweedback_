package com.example.demo;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.util.Map;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource() {
        String dbUrl = null;
        String dbUser = null;
        String dbPass = null;

        logger.info("Scanning environment variables for database configuration...");

        // Strategy 1: Look for any variable that is a Postgres URL
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String val = entry.getValue();
            if (val != null && val.startsWith("postgresql://")) {
                logger.info("Found Postgres URL in variable: {}", entry.getKey());
                try {
                    URI dbUri = new URI(val);
                    String userInfo = dbUri.getUserInfo();
                    if (userInfo != null && userInfo.contains(":")) {
                        dbUser = userInfo.split(":")[0];
                        dbPass = userInfo.split(":")[1];
                    }
                    dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + (dbUri.getPort() == -1 ? 5432 : dbUri.getPort()) + dbUri.getPath();
                    break;
                } catch (Exception e) {
                    logger.warn("Failed to parse URL from {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }

        // Strategy 2: Look for individual PG variables (Railway standard)
        if (dbUrl == null) {
            String host = System.getenv("PGHOST");
            String port = System.getenv("PGPORT");
            String db = System.getenv("PGDATABASE");
            String user = System.getenv("PGUSER");
            String pass = System.getenv("PGPASSWORD");

            if (host != null && !host.trim().isEmpty()) {
                logger.info("Found PGHOST variable. Constructing URL manually.");
                dbUrl = "jdbc:postgresql://" + host + ":" + (port != null ? port : "5432") + "/" + (db != null ? db : "railway");
                dbUser = user;
                dbPass = pass;
            }
        }

        // Strategy 3: Fallback for local development
        if (dbUrl == null) {
            logger.info("No Railway database variables found. Falling back to local localhost configuration.");
            dbUrl = "jdbc:postgresql://localhost:5432/maweed_db";
            dbUser = "postgres";
            dbPass = "qwert12345";
        }

        logger.info("Final Database URL: {}", dbUrl);

        return DataSourceBuilder.create()
                .url(dbUrl)
                .username(dbUser)
                .password(dbPass)
                .type(HikariDataSource.class)
                .build();
    }
}
