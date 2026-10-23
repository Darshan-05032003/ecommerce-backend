package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.auth.RefreshTokenService.Rejected;
import com.darshan.ecommerce.auth.RefreshTokenService.Rotated;
import com.darshan.ecommerce.common.logging.LogMasking;
import com.darshan.ecommerce.common.security.InvalidTokenException;
import com.darshan.ecommerce.common.security.UserPrincipal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: each step commits on its own, so a rejected refresh still
// keeps the revocation that RefreshTokenService wrote while examining the token.
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (AuthenticationException e) {
            log.info("Login failed for {}", LogMasking.email(request.email()));
            throw e;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return tokens(principal, refreshTokenService.issue(principal.getId()));
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        return switch (refreshTokenService.rotate(request.refreshToken())) {
            case Rotated rotated -> tokens(CustomUserDetailsService.toPrincipal(rotated.user()), rotated.rawToken());
            case Rejected rejected -> throw new InvalidTokenException("Invalid or expired refresh token");
        };
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse tokens(UserPrincipal principal, String refreshToken) {
        return new AuthResponse(jwtService.issueAccessToken(principal), refreshToken,
                jwtService.accessTokenTtl().toSeconds());
    }
}
