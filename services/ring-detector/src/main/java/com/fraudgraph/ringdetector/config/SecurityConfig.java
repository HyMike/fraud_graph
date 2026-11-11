package com.fraudgraph.ringdetector.config;

import com.fraudgraph.ringdetector.constants.ApiPaths;
import com.fraudgraph.ringdetector.enums.Role;
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

import static com.fraudgraph.ringdetector.constants.SecurityConstants.JWT_ISSUER;
import static com.fraudgraph.ringdetector.constants.SecurityConstants.MIN_SIGNING_KEY_BYTES;
import static com.fraudgraph.ringdetector.constants.SecurityConstants.ROLE_AUTHORITY_PREFIX;
import static com.fraudgraph.ringdetector.constants.SecurityConstants.ROLE_CLAIM;
import static com.fraudgraph.ringdetector.enums.Role.ADMIN;
import static com.fraudgraph.ringdetector.enums.Role.ANALYST;
import static com.fraudgraph.ringdetector.enums.Role.AUDITOR;
import static com.fraudgraph.ringdetector.enums.Role.LEAD;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
                .requestMatchers(HttpMethod.POST, ApiPaths.AUTH + ApiPaths.LOGIN).permitAll()
                .requestMatchers(HttpMethod.POST, ApiPaths.CLUSTERS + ApiPaths.RECOMPUTE).hasAnyRole(roles(LEAD, ADMIN))
                .requestMatchers(HttpMethod.POST, ApiPaths.CLUSTERS + ApiPaths.CLUSTER_DECISION)
                    .hasAnyRole(roles(ANALYST, LEAD, ADMIN))
                .requestMatchers(HttpMethod.GET, ApiPaths.DECISIONS).hasAnyRole(roles(AUDITOR, LEAD, ADMIN))
                .requestMatchers(HttpMethod.GET, ApiPaths.CLUSTERS, ApiPaths.CLUSTERS + "/**", ApiPaths.DETECTION + "/**")
                    .hasAnyRole(roles(Role.values()))
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
        if (bytes.length < MIN_SIGNING_KEY_BYTES) {
            throw new IllegalStateException(
                "JWT_SIGNING_KEY must be at least " + MIN_SIGNING_KEY_BYTES + " bytes for HS256");
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
            JwtValidators.createDefaultWithIssuer(JWT_ISSUER)));
        return decoder;
    }

    /** Turns the token's "role" claim (e.g. "LEAD") into the authority ROLE_LEAD. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix(ROLE_AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** hasAnyRole() takes role names without the ROLE_ prefix, e.g. "LEAD". */
    private static String[] roles(Role... roles) {
        return Arrays.stream(roles).map(Role::name).toArray(String[]::new);
    }

    /** The dashboard calls this API directly from the browser; origins come from app.cors.allowed-origins. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiPaths.ALL_API, config);
        return source;
    }
}
