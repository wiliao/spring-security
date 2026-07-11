<!--
  oauth2-resource-server-architecture-and-workflow.md
  Purpose: Overview of the `oauth2-resource-server` module architecture and common resource server workflows
  Location: oauth2/oauth2-resource-server/docs/
-->

# OAuth2 Resource Server — Architecture and Workflow

This document describes the responsibilities and architecture of the `oauth2-resource-server` module: how it validates access tokens, enforces scope/claim checks, integrates with JWT/JWK machinery, supports introspection for opaque tokens, and common extension points for custom authorization logic.

Checklist

- Understand supported token types and validation strategies (JWT, introspection/opaque, DPoP-bound)
- See the request processing pipeline and filter integration points
- Review example HTTP interactions for token validation and introspection
- Learn about scope/claim checks, audience validation, and custom authorization hooks
- Find references to integration points (JwtDecoder, JWK Set, Introspection client)

## 1. Purpose and scope

The `oauth2-resource-server` module provides runtime components for protecting resource server endpoints using OAuth 2.0 access tokens. Responsibilities include:

- Validate presented access tokens (JWT or opaque) and establish an authenticated security principal.
- Optionally validate DPoP proofs when DPoP-bound tokens are used.
- Perform scope and claim checks for authorization decisions.
- Integrate with JWK Sets, `JwtDecoder`/`JwtEncoder`, and Introspection endpoints.
- Provide extension points for auditing, claim-to-authority mapping, and custom token validators.

This module is designed to be used in web resource servers (APIs) and integrates with Spring Security's filter chain to produce a `SecurityContext` that downstream handlers can rely on.

## 2. Supported validation strategies

1) Self-contained JWT validation
- Use `JwtDecoder` to verify the JWT signature (using JWK Set obtained from the Authorization Server), validate standard claims (iss, aud, exp, nbf, iat), and optionally validate extra claims (scope, scope format, azp, cnf for DPoP binding).
- Ideal when tokens are short-lived and resource servers can perform local validation without network calls.

2) Introspection for opaque tokens
- For opaque tokens, the resource server calls the Authorization Server's introspection endpoint (`/oauth2/introspect`) with client authentication to resolve token metadata (active, scope, client_id, exp, sub, etc.).
- This requires a configured `OAuth2TokenIntrospectionClient` or similar integration that performs introspection and translates the response into an authenticated principal.

3) Hybrid or fallback strategies
- Resource servers may attempt JWT validation first; if that fails and the token appears opaque, fall back to introspection.
- Consider caching introspection results with short TTLs to reduce load on the AS.

4) DPoP-bound token validation
- When tokens are DPoP-bound, the server verifies the DPoP proof (a JWS supplied by the client) and checks that the DPoP public key matches the token's `cnf.jkt` (JWK thumbprint) claim.
- DPoP validation binds the HTTP method and URL present in the proof and checks replay protections (jti uniqueness, iat window).

## 3. Request processing pipeline (filter chain)

Typical ordering in the HTTP filter chain:

- `SecurityContextHolderFilter`
- `AuthorizationServerContextFilter` (if present in same app)
- `AbstractPreAuthenticatedProcessingFilter` (anchor point)
- `OAuth2ResourceServerFilter` / `JwtAuthenticationFilter` / `BearerTokenAuthenticationFilter`
- `AuthorizationFilter`

Processing steps performed by the resource-server filter:

1. Extract access token and proof (Authorization header `Bearer <token>`, `DPoP` header if present). Also support `access_token` in body or query where allowed.
2. Determine token type (JWT vs opaque) heuristically or by configuration.
3. If JWT: call `JwtDecoder` → validate signature and claims. Create `JwtAuthenticationToken` (or equivalent) as authenticated principal.
4. If opaque: call introspection client → if active, map response to claims/authorities and create authenticated principal.
5. If DPoP: validate proof and ensure binding to token.
6. Store authenticated principal in `SecurityContext` for downstream authorization.

## 4. Token extraction and formats

- Standard extraction: `Authorization: Bearer <token>` header.
- Alternative locations: form-encoded body or query parameter (deprecated/less secure) — allow only when explicitly configured.
- DPoP: client sends a `DPoP` header containing a JWS proof; handle per RFC 8800.

## 5. Example flows

5.1 JWT validation (resource server)

Request:

GET /api/resource
Authorization: Bearer eyJhbGciOiJSUzI1NiI...

Server steps:
- Extract token from Authorization header.
- Use `JwtDecoder` to verify signature using key from JWK Set (AS `/.well-known/jwks.json`).
- Validate `iss` (matches expected issuer), `aud` (includes resource server audience), `exp` not expired, optionally `scope` contains required scope.
- Create `Authentication` (e.g., `JwtAuthenticationToken`) and set `SecurityContextHolder.getContext().setAuthentication(...)`.
- Downstream handlers authorize based on `hasAuthority('SCOPE_read')` or custom claim checks.

5.2 Introspection (opaque token)

Request:

GET /api/resource
Authorization: Bearer f7s6k3... (opaque)

Server steps:
- Call introspection endpoint with client credentials (or configured token) and token value.
- Receive response: { "active": true, "scope": "read write", "client_id": "abc", "exp": 162... , "sub":"user" }
- Map `scope` to authorities (e.g., `SCOPE_read`).
- Create `Authentication` and proceed.

## 6. Scope and claim checks

- Common patterns:
  - Map `scope` values to authorities: `scope` -> `SCOPE_<scope>` and use `hasAuthority('SCOPE_read')` in expressions.
  - Map custom claims (roles, groups, tenant) to granted authorities using a `JwtGrantedAuthoritiesConverter` or custom authority mapper.
  - Validate `aud` includes the resource server identifier.

Best practices
- Use fine-grained scopes for API methods, not monolithic scopes.
- Prefer claim-based role mapping for richer authorization decisions (e.g., `roles` claim or `groups`).

## 7. Caching and performance

- JWT validation is local and cheap (signature verification + claim checks). Cache JWK Set locally, respect cache headers and refresh on verification failures.
- Introspection is network-bound; cache introspection results (active=true) for a short TTL (e.g., a few seconds) to reduce load. Beware of revocation latency trade-offs.

## 8. Error handling and responses

- For missing or invalid tokens, return HTTP 401 Unauthorized with `WWW-Authenticate` header following RFC 6750. Include appropriate error and description only for debugging (avoid leaking info in production).
- For insufficient scope, return HTTP 403 Forbidden with `WWW-Authenticate: Bearer error="insufficient_scope", scope="..."`.

Examples

401 response when token is invalid:

HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token", error_description="Token expired"

403 response when insufficient scope:

HTTP/1.1 403 Forbidden
WWW-Authenticate: Bearer error="insufficient_scope", scope="read"

## 9. DPoP-specific checks and anti-replay

- DPoP proof validation includes verifying the JWS signature, checking that `htm` (HTTP method) and `htu` (HTTP URI) match the request, validating `iat` within an acceptable clock skew window, and ensuring `jti` has not been replayed within a short window.
- Implement a nonce/jti cache to detect replays. Store token-bound jtis with short TTL.

## 10. Customization and extension points

- `JwtDecoder` configuration: customize claim validators, allowed algorithms, and clock skew.
- `JwtGrantedAuthoritiesConverter` / `AuthoritiesMapper`: customize how claims/scopes translate to authorities.
- Introspection client: replace HTTP client, add caching, or enhance with backoff/retry.
- `BearerTokenResolver`: customize token extraction rules (header only, allow query param in certain routes, etc.).
- Add pre/post authentication handlers for auditing, telemetry, or rate limiting.

## 11. Integration with API gateways and proxies

- When deploying behind API gateways, decide whether the gateway or the application performs token validation. Gateways can perform authentication and forward validated principals via headers, but ensure headers are trusted only from the gateway (use mTLS or internal networks).

## 12. Operational considerations

- Monitor token validation latency, introspection call rates, and DPoP replay cache size.
- Rotate accepted signing keys per AS policy and ensure JWK Set refresh/configuration is in place.
- Log authentication failures with enough context for debugging while avoiding sensitive token leakage.

## 13. References

- RFC 6750 — The OAuth 2.0 Authorization Framework: Bearer Token Usage
- RFC 7662 — OAuth 2.0 Token Introspection
- RFC 8800 — DPoP: Demonstration of Proof-of-Possession
- RFC 7515 / 7517 / 7519 — JOSE/JWT specs

If you want, I can:
- Search the repo and list the exact resource-server classes/filters (e.g., `BearerTokenAuthenticationFilter`, introspection client) and their file paths, or
- Create a minimal example resource endpoint with `JwtDecoder` wiring and unit tests validating JWT and introspection paths.

