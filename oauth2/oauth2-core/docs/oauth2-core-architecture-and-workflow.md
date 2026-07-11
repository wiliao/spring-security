<!--
  oauth2-core-architecture-and-workflow.md
  Purpose: Overview of the `oauth2-core` module architecture and common responsibilities (token model, storage, generation, validation)
  Location: oauth2/oauth2-core/docs/
-->

# OAuth2 Core — Architecture and Workflow

This document describes the core primitives provided by the `oauth2-core` module: the domain model for tokens/authorizations, token generation and validation pipelines, storage patterns, and extension points used by authorization-server and client modules.

Checklist

- Understand core domain objects (Authorization, Token, RegisteredClient references)
- Review token generation pipeline and token customization hooks
- Learn about token formats (self-contained JWT vs reference/opaque) and DPoP binding
- See persistence and revocation/introspection patterns
- Find extension points and integration tips

## 1. Purpose and scope

The `oauth2-core` module centralizes protocol-neutral domain models and helper abstractions used by both the authorization server and client components. It focuses on:

- Representing runtime authorizations and tokens (`OAuth2Authorization`, `OAuth2Token`, `OAuth2AccessToken`, `OAuth2RefreshToken`, optional `OidcIdToken`).
- A token minting pipeline (`OAuth2TokenContext`, `OAuth2TokenGenerator`) and default token generators (`JwtGenerator`, `OAuth2AccessTokenGenerator`, `OAuth2RefreshTokenGenerator`).
- Token claim set and header composition, and token customization hooks for applications.
- Common token-related behaviors: introspection, revocation, storage, expiration, and binding (DPoP / mTLS).

This separation keeps protocol logic (endpoints, filters, converters) out of core while providing rich, reusable token semantics.

## 2. Core domain model

- `OAuth2Authorization`
  - The runtime record for an issued authorization instance. Contains `registeredClientId`, `principalName`, `authorizationGrantType`, authorized scopes, a map of issued `Token<?>` instances, and a free-form attributes map.
  - Used as the persistence unit backing tokens and for introspection / revocation operations.

- `OAuth2Token` and concrete subtypes
  - `OAuth2AccessToken` — `tokenValue`, `issuedAt`, `expiresAt`, `scopes`, `tokenType` (Bearer or DPoP-bound metadata stored in attributes).
  - `OAuth2RefreshToken` — opaque value with issuance/expiry semantics.
  - `OidcIdToken` — ID Token claims and headers (JWT semantics) when OIDC is used.

- `RegisteredClient` reference
  - `OAuth2Authorization` stores the `registeredClientId` (not the full registration) to avoid duplication. The `RegisteredClientRepository` is used to resolve client metadata when needed.

## 3. Token generation pipeline

The module exposes an extensible generation pipeline:

```
OAuth2TokenContext  -->  OAuth2TokenGenerator.generate(context) : T | null
```

- `OAuth2TokenContext` contains `RegisteredClient`, `AuthorizationServerContext`, principal, authorized scopes, `OAuth2TokenType`, `AuthorizationGrantType`, and additional attributes (DPoP proof, requested time-to-live, etc.).
- `DelegatingOAuth2TokenGenerator` composes delegates in order: `JwtGenerator` (if JwtEncoder/JWKSource present), `OAuth2AccessTokenGenerator` (opaque/reference tokens), and `OAuth2RefreshTokenGenerator`.
- `JwtGenerator` builds the JWT header/claims and invokes configured `OAuth2TokenCustomizer<JwtEncodingContext>` implementations (application hooks) before encoding.
- `OAuth2AccessTokenGenerator` produces opaque tokens plus a claims set used for introspection when reference tokens are used.

Customization hooks
- `OAuth2TokenCustomizer<JwtEncodingContext>` and `OAuth2TokenCustomizer<OAuth2TokenClaimsContext>` allow applications to add claims or modify headers for tokens issued by the default generators.

## 4. Token formats and binding

- Self-contained (JWT) access tokens: signed JWTs issued by `JwtGenerator`. These carry claims (iss, sub, aud, iat, exp, jti, scope) and can be validated offline by resource servers using the AS's JWK Set.
- Reference (opaque) access tokens: random opaque values stored/linked to `OAuth2Authorization` and resolved via introspection (`/oauth2/introspect`). The server issues an opaque value and stores the associated claims/state for later lookup.

Binding techniques
- DPoP: Demonstration of Proof-of-Possession tokens are supported. The token generation pipeline and request handling accept a DPoP proof (JWT), and the issued access token can be bound to the DPoP public key (jkt) claim. The token context exposes the DPoP proof and allows token customizers to add cnf claims.
- mTLS: Similar binding using certificate thumbprints when mTLS client authentication is used.

## 5. Persistence and repositories

- `OAuth2AuthorizationService` is the persistence SPI for storing `OAuth2Authorization` records. Implementations include in-memory and JDBC-based variants.
- Persistence responsibilities:
  - Save issued tokens and updated authorization state (e.g., when a refresh token is rotated or revoked).
  - Lookup by token value (for introspection, code exchange), and by id.
  - Mark tokens as invalidated on revocation.

Design notes
- Storing token claims vs deriving them: when using reference tokens, store the claim set (or sufficient state) to answer introspection queries; for JWTs, minimal storage is needed (depending on logout/blacklist requirements) since tokens are self-contained.

## 6. Introspection and revocation

- Introspection (`/oauth2/introspect`): resolves a presented token (opaque or JWT) and returns an `active` boolean and associated metadata (scope, client_id, exp, iat, sub). For JWTs, validation may be performed (signature, exp) and then introspection may optionally return claims without storing state.
- Revocation (`/oauth2/revoke`): marks a stored token as revoked in the `OAuth2Authorization` record. For JWTs, revocation may require maintaining a revocation list or rotating signing keys.

Best practices
- Introspection endpoints should require client authentication and return minimal information to unauthorized callers.
- Use short-lived access tokens and rely on refresh tokens (rotated) to reduce the need for server-side revocation.

## 7. Authorization code handling and PKCE

- The core models support storing the original `OAuth2AuthorizationRequest` as an attribute on `OAuth2Authorization` to assist in code exchange validation, PKCE verification, and to reproduce claims needed for token issuance.
- PKCE: `code_challenge` and `code_challenge_method` are validated when the code is exchanged at the token endpoint. The stored code record must contain the code_challenge to verify the `code_verifier` at exchange time.

## 8. Token rotation and refresh semantics

- Refresh tokens can be rotated on use: the token generator may issue a new refresh token and invalidate the previous one. The `OAuth2Authorization` record is updated accordingly.
- Considerations:
  - Reuse vs rotation policies must align with `TokenSettings` (e.g., `reuseRefreshTokens=true/false`).
  - Prevent refresh token replay by storing previous refresh token identifiers and marking them invalid after rotation.

## 9. Eventing and auditing

- The core module can emit events (or callback hooks) when tokens are issued, refreshed, revoked, or when authorizations are created. Applications can use these to audit, notify, or integrate with downstream systems.

## 10. Extension points and integration

- `OAuth2TokenGenerator` — supply a custom generator to replace or extend default behavior (e.g., a custom JWT with different signing strategy or a tokens-as-a-service integration).
- `OAuth2AuthorizationService` — implement custom storage (Redis, Cassandra, encrypted DB columns) to meet scalability and compliance needs.
- `OAuth2TokenCustomizer` — hook into claims/header generation for JWTs and opaque token claim sets.
- `Authorization` attributes map — flexible per-authorization metadata used by downstream components (e.g., device info, DPoP thumbprint, audience hints).

## 11. Security considerations

- Sign and rotate keys: for JWTs, rotate signing keys and publish JWK Set so resource servers can validate.
- Short lifetimes for access tokens: keep them small and rely on refresh flows to limit exposure.
- Protect introspection and revocation endpoints: require client authentication and limit information returned to callers.
- Protect persisted token state: encrypt sensitive fields (refresh tokens) at rest and restrict DB access.

## 12. References

- RFC 6749 — OAuth 2.0
- RFC 7519 — JSON Web Token (JWT)
- RFC 7517 — JSON Web Key (JWK)
- RFC 7636 — PKCE
- RFC 8628 — Device Flow (where device-code handling touches authorization persistence)
- RFC 8800 — DPoP

If you want, I can:
- Search the codebase for the exact core classes (e.g., `OAuth2Authorization`, `OAuth2AuthorizationService`, `JwtGenerator`, `OAuth2TokenCustomizer`) and list their file paths, or
- Draft a short guide showing how to implement a custom `OAuth2AuthorizationService` backed by Redis with example code.

