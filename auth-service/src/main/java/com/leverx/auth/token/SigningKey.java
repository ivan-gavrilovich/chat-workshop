package com.leverx.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "signing_key")
public class SigningKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String kid;

    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    @Column(name = "private_key", nullable = false)
    private byte[] privateKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SigningKey() {
    }

    public SigningKey(String kid, byte[] publicKey, byte[] privateKey) {
        this.kid = kid;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getKid() {
        return kid;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }
}
