package com.leverx.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SigningKeyRepository extends JpaRepository<SigningKey, Long> {

    Optional<SigningKey> findFirstByOrderByCreatedAtDesc();
}
