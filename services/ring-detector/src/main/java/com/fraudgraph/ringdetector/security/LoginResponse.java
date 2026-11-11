package com.fraudgraph.ringdetector.security;

import java.time.Instant;

public record LoginResponse(String token, String username, Role role, Instant expiresAt) {
}
