package com.darshan.ecommerce.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Kept separate from the application class so that web slice tests do not bootstrap JPA auditing.
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaConfig {
}
