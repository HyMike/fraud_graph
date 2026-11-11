package com.fraudgraph.ringdetector.dto.response;

import com.fraudgraph.ringdetector.enums.Role;
import java.time.Instant;

public record LoginResponse(String token, String username, Role role, Instant expiresAt) {
}
