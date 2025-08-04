package com.cosmicdoc.inventoryservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;

@Configuration
//@EnableWebSecurity
//@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}")
    private String jwtSecretKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // No need to configure cors here - our CorsFilter will handle it
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll() // TEMPORARILY permit all requests for API testing
                )
                // Disable JWT validation for testing
                .oauth2ResourceServer(oauth2 -> oauth2.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "https://healthcare-app-1078740886343.us-central1.run.app",
                "https://healthcare-app-145837205370.asia-south1.run.app"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Auth-Token", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("X-Auth-Token"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Manually creates the JwtDecoder bean to validate tokens using our shared secret key.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = Base64.getEncoder().encode(jwtSecretKey.getBytes());
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * This is the definitive fix for the 403 Forbidden error.
     * This converter tells Spring Security HOW to find the roles (authorities)
     * within your custom JWT claims.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // By default, Spring looks for a 'scope' or 'scp' claim. We need to override this.
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Tell the converter to look for authorities in our custom 'role' claim.
        grantedAuthoritiesConverter.setAuthoritiesClaimName("role");
        // By default, it adds a "SCOPE_" prefix. We remove it to handle roles cleanly.
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtConverter;
    }
}