<!--
  oauth2-jose-architecture-and-workflow.md
  Purpose: Overview of the `oauth2-jose` module responsibilities and how JOSE primitives (JWK, JWS, JWE, JWA) are used in the project
  Location: oauth2/oauth2-jose/docs/
-->

# OAuth2 JOSE — Architecture and Workflow

This document describes the responsibilities of the `oauth2-jose` module (JSON Object Signing and Encryption — JOSE), how it manages keys (JWK/JWK Set), performs signing (JWS) and encryption (JWE), and how these primitives integrate with token generation/validation in the rest of the project.

Checklist

- Understand JOSE building blocks used by the project (JWK, JWK Set, JWS, JWE, JWA)
- Learn how keys are provided to token generators / validators (`JWKSource`, `JwtEncoder`/`JwtDecoder` integration)
- See recommended key rotation, kid selection, and algorithm practices
- Review examples for common usages: ID Tokens, Access Token signing, Client Assertions, DPoP proofs
- Find references and next steps to inspect implementation classes in this repo

## 1. Scope and standards implemented

This module provides JOSE-related utilities and integration points used by the OAuth2/OIDC modules:

| Feature | Spec |
|---|---|
| JSON Web Key (JWK) / JWK Set | RFC 7517 |
| JSON Web Signature (JWS) | RFC 7515 |
| JSON Web Encryption (JWE) | RFC 7516 |
| JSON Web Algorithms (JWA) | RFC 7518 / RFC 8037 |
| JSON Web Token (JWT) encoding/decoding helpers | RFC 7519 |

The primary responsibilities are key management (exposing keys via JWKSource/JWK Set), signing tokens (JWS) for ID Tokens and optionally access tokens, and supporting DPoP and client assertion signing.

## 2. Core concepts and classes (conceptual)

- JWK / JWK Set: JSON representations of cryptographic keys. A JWK Set (`{ "keys": [...] }`) is published by the authorization server for resource servers to validate JWT signatures.
- JWS: Signed payloads (compact or JSON serialization) used to assert integrity and authenticity of tokens (ID Token, client assertions, DPoP proofs).
- JWE: Encrypted payloads (rare for access tokens in many deployments) used when confidentiality of token contents is required.
- JWA: Algorithm families for signatures (RS256, ES256, PS256), encryption (RSA-OAEP, A128GCM), and key management.
- `JWKSource<SecurityContext>`: abstraction that provides keys to encoders/decoders at runtime.
- `JwtEncoder` / `JwtDecoder`: components that produce and verify JWTs (used by token generators and resource servers).

In this project the `oauth2-jose` module supplies utilities and wiring so higher-level modules (oauth2-core, authorization-server) can sign tokens and expose a JWK Set endpoint.

## 3. Key provisioning and `JWKSource`

- The runtime needs a source of asymmetric keys for signing (private keys) and the corresponding public keys to publish in a JWK Set. `JWKSource<SecurityContext>` is the standard abstraction used by Nimbus and integration layers to supply keys.
- Common provisioning patterns:
  - Embedded keys: keys generated or bundled with the application (useful for demos/tests). Not recommended for production unless protected.
  - Keystore-backed: load keys from a PKCS#12 / JKS keystore and convert to JWKs.
  - Hardware-backed: HSM-backed keys exposed via a PKCS#11 provider or cloud KMS; private keys never leave HSM and a signer wrapper is used.
  - Dynamic: rotation endpoints or a key-management system that updates the `JWKSource` at runtime.

- The module typically exposes a `JwkSetEndpoint` (e.g. `GET /oauth2/jwks`) that returns the public JWK Set (only public members of each key) allowing resource servers to retrieve keys to verify signatures.

Key selection and `kid`
- When signing, the signer should include a `kid` (key id) header that identifies which key in the JWK Set was used. The `kid` allows decoders to quickly select the correct public key for verification.
- When multiple active keys exist (rotation window), include all public keys in the JWK Set. The server signs with the active key and publishes both the active and previous key(s) until they are retired.

## 4. Signing (JWS) and JWT generation

- Typical signing use-cases in the project:
  - ID Tokens (OIDC): always signed (usually JWS, using RS256/ES256/PS256 per configuration)
  - Access Tokens: may be signed JWTs (self-contained) or opaque tokens. When self-contained, they are JWS-signed so resource servers can validate without introspection.
  - Client Assertions: signed JWTs used for `private_key_jwt` client authentication.
  - DPoP proofs: client-generated JWS used to bind requests to a key pair.

- Signing flow (high-level):
  1. Build JWT claims and headers (iss, sub, aud, iat, exp, jti, scope, azp, etc.).
  2. Choose signing algorithm (e.g., RS256, ES256, PS256) based on `RegisteredClient` / server policy.
  3. Select signing key and include `kid` header.
  4. Use `JwtEncoder`/JWS signer to produce a compact JWS.

Algorithm selection
- Prefer RSASSA-PSS (PS256/PS384/PS512) or ECDSA (ES256/ES384) where supported. RSASSA-PKCS1v1_5 (RS256) is widely interoperable but PS256 offers better security properties.
- Use algorithm negotiation aligned with discovery metadata (advertise supported signing algorithms in the provider configuration).

## 5. Encryption (JWE)

- JWE is used when token contents must be kept confidential between the issuer and the intended consumer. Typical usages are less common for access tokens in modern OAuth deployments; many systems instead use short-lived access tokens or TLS for confidentiality.
- When JWE is used:
  - Choose key management algorithm (e.g., RSA-OAEP, ECDH-ES) and content encryption algorithm (A128GCM, A256GCM).
  - The server encrypts the JWT and returns an encrypted JWE compact serialization. Consumers possessing the decryption key decrypt and validate the nested (signed) JWT if applicable.

## 6. Token validation and `JwtDecoder`

- Resource servers or components that validate JWTs should:
  1. Fetch JWK Set from the AS (cache it with reasonable expiry and respect cache headers).
  2. Select the key by `kid` (or attempt algorithm-specific verification if no `kid`).
  3. Verify signature, then validate claims (iss, aud, exp, iat, nbf, azp, scope).

- Use the `JwtDecoder` abstraction where available; it encapsulates key retrieval, signature verification, and claim validation.

## 7. Key rotation and lifecycle

- Key rotation strategy:
  - Generate a new key pair, assign a new `kid`, provision public key in the published JWK Set, and start signing new tokens with the new key.
  - Keep previous keys in the JWK Set for a rotation window to allow previously issued tokens to be validated.
  - After rotation window expiry, remove retired keys from the JWK Set and securely delete private material.

- Plan rotation for both planned (regular) rotations and emergency rotations (compromise).

## 8. DPoP and client assertion specifics

- DPoP: clients generate a JWS proof bound to the HTTP method + URL and include it in the `DPoP` header. The server validates the DPoP proof and may issue DPoP-bound access tokens that include a `cnf` claim containing the key thumbprint.

- Client Assertions (`private_key_jwt`): the client signs a JWT with its private key (JWS) including `iss`, `sub`, `aud` (token endpoint), `jti`, and `exp`. The AS validates the signature using the client's registered JWKs.

## 9. Security considerations

- Protect private keys: store private key material in an HSM or secure keystore. If using files, restrict filesystem permissions and rotate regularly.
- Limit key exposure: publish only public JWKs. Never include private parameters (`d`, `p`, `q`, etc.) in published JWK Sets.
- Use strong algorithms and prefer PSS (PS256) or ECDSA (ES256) over PKCS#1 v1.5 RSA where feasible.
- Enforce `kid` and algorithm checks on verification to mitigate key confusion attacks.
- Cache JWK Sets but refresh periodically and on verification failures (to recover from recent rotations).

## 10. Integration points in this project

- `JwtEncoder` / `JwtDecoder` usage: token generators (JwtGenerator) call encoders when producing signed JWTs; resource-server components use decoders for verification.
- `JWK Source` provider beans: the project wires a `JWKSource<SecurityContext>` into the encoder/decoder chain; the `JwkSetEndpointFilter` serves the public keys for discovery.
- Token customizers may add JOSE headers or claims; ensure any added header entries are consistent with published keys/algorithms.

## 11. References

- RFC 7515 — JSON Web Signature (JWS)
- RFC 7516 — JSON Web Encryption (JWE)
- RFC 7517 — JSON Web Key (JWK)
- RFC 7518 — JSON Web Algorithms (JWA)
- RFC 7519 — JSON Web Token (JWT)
- RFC 8037 — EdDSA and related algorithms

If you want, I can:
- Search the codebase and list the exact classes that implement JOSE integration (e.g., `JwkSetEndpointFilter`, `JwtEncoder` implementations, `JwtGenerator`) and show key methods, or
- Draft a short example showing how to configure a `JWKSource` backed by a PKCS#12 keystore and publish a JWK Set endpoint.

