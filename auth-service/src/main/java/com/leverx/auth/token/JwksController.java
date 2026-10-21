package com.leverx.auth.token;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final KeyProvider keyProvider;

    public JwksController(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Public keys for verifying tokens issued by this service (used by the room booking service).
     */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(keyProvider.rsaKey().toPublicJWK()).toJSONObject();
    }
}
