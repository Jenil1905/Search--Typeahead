package com.example.typeahead.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <h1>WebConfig</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This configuration class enables Cross-Origin Resource Sharing (CORS) globally for our REST endpoints.
 * 
 * <h3>What problem it solves:</h3>
 * Browsers enforce the Same-Origin Policy, which prevents a web page from making requests to a different
 * domain (port, protocol, or host) than the one that served it. Since our React frontend runs on
 * <code>http://localhost:5173</code> (or dynamic Vite port) and the Spring Boot backend runs on
 * <code>http://localhost:8080</code>, browser requests would be blocked by default. This config explicitly permits
 * cross-origin requests.
 * 
 * <h3>How it works internally:</h3>
 * Spring registers a CORS filter that intercepts incoming HTTP requests. If the request is a CORS pre-flight
 * request (HTTP OPTIONS), it adds headers like <code>Access-Control-Allow-Origin</code>,
 * <code>Access-Control-Allow-Methods</code>, and <code>Access-Control-Allow-Headers</code> to the response.
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What is CORS, and why is it needed?</b><br>
 *       A: CORS stands for Cross-Origin Resource Sharing. It is a security mechanism that allows web servers
 *       to declare which origins are permitted to access resources on the server.</li>
 *   <li><b>Q: What is a preflight request in CORS?</b><br>
 *       A: For non-simple requests (e.g. POST with JSON body), the browser first sends an <code>OPTIONS</code>
 *       request to the server to verify if the actual request is safe to send. If the server approves, the browser
 *       sends the actual request.</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Allow CORS on all endpoints
                .allowedOrigins("*") // Allow all origins for student demo (can be narrowed to http://localhost:5173)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600); // Cache preflight response for 1 hour to reduce network overhead
    }
}

/*
 ==================================================================================
 VIVA NOTES: CORS SECURITY
 ==================================================================================
 1. CORS is a browser-side policy. The server executes the request, but the browser blocks the response
    from being read by Javascript if the CORS headers are missing.
 2. In production, <code>allowedOrigins("*")</code> is dangerous. It should be restricted to the specific
    domain of the frontend application (e.g., https://search.example.com).
 ==================================================================================
*/
