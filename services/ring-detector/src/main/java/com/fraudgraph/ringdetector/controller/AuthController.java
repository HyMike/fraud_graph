package com.fraudgraph.ringdetector.controller;

import com.fraudgraph.ringdetector.constants.ApiPaths;
import com.fraudgraph.ringdetector.dto.request.LoginRequest;
import com.fraudgraph.ringdetector.dto.response.LoginResponse;
import com.fraudgraph.ringdetector.entity.AppUser;
import com.fraudgraph.ringdetector.repository.AppUserRepository;
import com.fraudgraph.ringdetector.service.TokenService;
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
@RequestMapping(ApiPaths.AUTH)
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

    @PostMapping(ApiPaths.LOGIN)
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
