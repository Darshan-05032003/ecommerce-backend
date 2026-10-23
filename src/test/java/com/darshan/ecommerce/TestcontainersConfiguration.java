package com.darshan.ecommerce;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// One MySQL container for the whole test JVM, shared by every Spring context that imports this
// configuration (the full integration context, the repository slices, the dev seeder test).
// The container is started once, when this class is first used, and is deliberately not a bean:
// Spring stops container beans when their context closes, which would pull the database away
// from the other contexts. Ryuk removes the container when the JVM exits.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    static {
        MYSQL.start();
    }

    @Bean
    DynamicPropertyRegistrar mysqlProperties() {
        return registry -> {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        };
    }
}
