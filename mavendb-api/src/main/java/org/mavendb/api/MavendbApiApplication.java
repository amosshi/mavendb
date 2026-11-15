package org.mavendb.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Read-only Maven Central metadata API, served from the SQLite file built by mavendb-loader.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MavendbApiApplication {

    /**
     * Start the API.
     *
     * @param args Spring Boot arguments, like <code>--mavendb.api.sqlite.path=/data/mavendb.sqlite</code>
     */
    public static void main(String[] args) {
        SpringApplication.run(MavendbApiApplication.class, args);
    }
}
