package com.leverx.auth.token;

import com.leverx.auth.user.AuthDtos.AuthResponse;
import com.leverx.auth.user.AuthDtos.UserResponse;
import com.leverx.auth.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final String keyId;
    private final String issuer;
    private final Duration ttl;

    public TokenService(JwtEncoder jwtEncoder,
                        KeyProvider keyProvider,
                        @Value("${auth.jwt.issuer}") String issuer,
                        @Value("${auth.jwt.ttl}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.keyId = keyProvider.rsaKey().getKeyID();
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public AuthResponse issue(User user) {
        var now = Instant.now();
        var expiresAt = now.plus(ttl);
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiresAt, UserResponse.from(user));
    }
}
