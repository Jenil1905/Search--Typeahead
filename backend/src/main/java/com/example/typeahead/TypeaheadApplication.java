package com.example.typeahead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <h1>TypeaheadApplication</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This is the bootstrap class for our Spring Boot application. It initializes the Spring application
 * context, starts the embedded Tomcat server, and scans all sub-packages for Spring components
 * (Controllers, Services, Repositories).
 * 
 * <h3>What problem it solves:</h3>
 * It automates the setup, configuration, and bootstrap process of a Java web application. Instead of
 * writing complex servlet configurations and manual dependency injection wiring, Spring Boot initializes
 * the application using convention-over-configuration.
 * 
 * <h3>How it works internally:</h3>
 * <ul>
 *   <li>The <code>@SpringBootApplication</code> annotation is a convenience annotation that combines
 *       <code>@Configuration</code>, <code>@EnableAutoConfiguration</code>, and <code>@ComponentScan</code>.</li>
 *   <li><code>@EnableScheduling</code> tells Spring to look for methods annotated with <code>@Scheduled</code>
 *       and run them on a background task scheduler. This is critical for our 30-second Batch Write System.</li>
 *   <li><code>SpringApplication.run(...)</code> launches the application, starting the Spring Container,
 *       creating beans, injecting dependencies, and exposing endpoints.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What does @SpringBootApplication combine?</b><br>
 *       A: @Configuration (declares beans), @ComponentScan (scans for beans in sub-packages), and
 *       @EnableAutoConfiguration (automatically configures beans based on classpath dependencies, e.g. setting up
 *       a Postgres DataSource when the postgres driver is on the classpath).</li>
 *   <li><b>Q: Why do we need @EnableScheduling here?</b><br>
 *       A: By default, Spring's scheduling capabilities are disabled. This annotation activates a task scheduler
 *       capable of running background tasks like our batch flushing logic.</li>
 * </ol>
 */
@SpringBootApplication
@EnableScheduling
public class TypeaheadApplication {

    public static void main(String[] args) {
        SpringApplication.run(TypeaheadApplication.class, args);
    }
}

/*
 ==================================================================================
 VIVA NOTES: SPRING BOOT BOOTSTRAP PROCESS
 ==================================================================================
 1. Spring Boot initializes a Spring ApplicationContext (specifically AnnotationConfigServletWebServerApplicationContext).
 2. It performs Component Scanning starting from the package where the main class resides (com.example.typeahead).
 3. Auto-Configuration: It checks the POM dependencies and configures details like JPA entity managers,
    transaction managers, connection pools (HikariCP), and Redis templates automatically.
 4. Embedded Server: It launches Tomcat on port 8080 (or as configured in application.yml).
 ==================================================================================
*/
