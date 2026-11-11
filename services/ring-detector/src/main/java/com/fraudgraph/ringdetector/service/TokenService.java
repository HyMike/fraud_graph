package com.fraudgraph.ringdetector.service;

import com.fraudgraph.ringdetector.dto.response.LoginResponse;
import com.fraudgraph.ringdetector.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

import static com.fraudgraph.ringdetector.constants.SecurityConstants.JWT_ISSUER;
import static com.fraudgraph.ringdetector.constants.SecurityConstants.ROLE_CLAIM;

/** Issues the short-lived access token returned by POST /api/auth/login. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration ttl;

    public TokenService(JwtEncoder jwtEncoder, @Value("${auth.jwt.ttl}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
    }

    public LoginResponse issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(JWT_ISSUER)
            .subject(user.getUsername())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(ROLE_CLAIM, user.getRole().name())
            .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, user.getUsername(), user.getRole(), expiresAt);
    }
}
