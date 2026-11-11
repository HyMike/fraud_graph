package com.fraudgraph.ringdetector.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stateless JWT auth: POST /api/auth/login exchanges a username/password for a
 * signed token, and every other /api call must carry it as a Bearer header.
 * No sessions or cookies, so CSRF protection is not needed.
 *
 * Endpoint access by role (ADMIN is a superuser and appears everywhere):
 *   GET  /api/clusters, /api/clusters/{id}/graph, /api/detection/**  any role
 *   POST /api/clusters/{id}/decision                                  ANALYST, LEAD, ADMIN
 *   POST /api/clusters/recompute                                      LEAD, ADMIN
 *   GET  /api/decisions                                               AUDITOR, LEAD, ADMIN
 */
@Configuration
public class SecurityConfig {

    private static final int MIN_KEY_LENGTH = 32;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> { })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Let Spring's error page render for errors raised by permitted requests
                // (e.g. a 400 for a malformed body) instead of turning them into a 401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/clusters/recompute").hasAnyRole("LEAD", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/clusters/*/decision").hasAnyRole("ANALYST", "LEAD", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/decisions").hasAnyRole("AUDITOR", "LEAD", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/clusters", "/api/clusters/**", "/api/detection/**")
                    .hasAnyRole("ANALYST", "LEAD", "AUDITOR", "ADMIN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSigningKey(@Value("${auth.jwt.signing-key}") String signingKey) {
        byte[] bytes = signingKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                "JWT_SIGNING_KEY must be at least " + MIN_KEY_LENGTH + " bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        // Default validators check exp/nbf; also require that we issued it.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(TokenService.ISSUER)));
        return decoder;
    }

    /** Turns the token's "role" claim (e.g. "LEAD") into the authority ROLE_LEAD. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** The Angular dev server (localhost:4200) calls this API (localhost:8081) directly. */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
