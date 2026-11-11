package com.fraudgraph.ringdetector.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login only — there is no logout endpoint. Tokens are stateless and short-lived
 * (auth.jwt.ttl); logging out means the dashboard discards its token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Compared against when the username does not exist, so an unknown user takes
    // as long to reject as a wrong password and response time does not reveal
    // which usernames are real.
    private final String dummyHash;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        String username = request.username() == null ? "" : request.username().strip();
        String password = request.password() == null ? "" : request.password();

        AppUser user = users.findByUsername(username).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !user.isEnabled() || !passwordMatches) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return tokenService.issue(user);
    }
}
