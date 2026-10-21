CREATE TABLE app_user
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- RSA key pair used to sign JWTs; persisted so issued tokens survive restarts
CREATE TABLE signing_key
(
    id          BIGSERIAL PRIMARY KEY,
    kid         VARCHAR(64) NOT NULL UNIQUE,
    public_key  BYTEA       NOT NULL,
    private_key BYTEA       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
