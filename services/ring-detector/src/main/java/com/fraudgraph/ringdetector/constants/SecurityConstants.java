package com.fraudgraph.ringdetector.constants;

/** JWT and Spring Security values shared by SecurityConfig and TokenService. */
public final class SecurityConstants {

    /** The "iss" claim on every token this service issues, and the only issuer it accepts. */
    public static final String JWT_ISSUER = "ring-detector";

    /** Token claim holding the user's Role name (e.g. "LEAD"). */
    public static final String ROLE_CLAIM = "role";

    /** Spring's prefix for role authorities: claim "LEAD" becomes authority ROLE_LEAD. */
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SIGNING_KEY_BYTES = 32;

    private SecurityConstants() {
    }
}
