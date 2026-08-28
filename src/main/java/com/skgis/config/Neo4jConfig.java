package com.skgis.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {
    private final Driver driver;

    public Neo4jConfig(Driver driver) {
        this.driver = driver;
    }

    public Driver getDriver() {
        return driver;
    }
}
