package com.leverx.auth.token;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

/**
 * Loads the RSA key pair used for signing JWTs from the database, generating it on first start.
 * Other services verify tokens with the public part published at /.well-known/jwks.json.
 */
@Component
public class KeyProvider {

    private final RSAKey rsaKey;

    public KeyProvider(SigningKeyRepository repository, TransactionTemplate transactionTemplate) {
        var signingKey = transactionTemplate.execute(status -> repository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(() -> repository.save(generate())));
        this.rsaKey = toRsaKey(signingKey);
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    private static SigningKey generate() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            return new SigningKey(UUID.randomUUID().toString(),
                    keyPair.getPublic().getEncoded(), keyPair.getPrivate().getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate RSA key pair", e);
        }
    }

    private static RSAKey toRsaKey(SigningKey key) {
        try {
            var factory = KeyFactory.getInstance("RSA");
            var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(key.getPublicKey()));
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(key.getPrivateKey()));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(key.getKid()).build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot load RSA key pair", e);
        }
    }
}
