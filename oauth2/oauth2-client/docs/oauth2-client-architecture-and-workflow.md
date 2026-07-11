<!--
  oauth2-client-architecture-and-workflow.md
  Purpose: Overview of the `oauth2-client` module architecture and common OAuth2/OIDC client workflows
  Location: oauth2/oauth2-client/docs/
-->

# OAuth2 Client — Architecture and Workflow

This document describes the internal architecture of the `oauth2-client` module and the common OAuth2 / OpenID Connect client flows it supports (Authorization Code with PKCE, Client Credentials, Device Flow, Refresh, and token handling). It also highlights extension points and integration notes for applications.

Checklist

- Understand the module responsibilities and core classes
- See configuration and common extension points
- Review typical flows (sequence and HTTP message examples)
- Find pointers to server-side counterpart endpoints and validation notes

## 1. Scope and standards implemented

This module provides client-side building blocks to act as an OAuth 2.0 / OpenID Connect Relying Party or OAuth2 client. Supported flows and features:

| Capability | Specification |
|---|---|
| Authorization Code Grant (with PKCE) | RFC 6749, RFC 7636 |
| Client Credentials Grant | RFC 6749 |
| Device Authorization Grant | RFC 8628 |
| Refresh Token Grant | RFC 6749 |
| DPoP (Demonstration of Proof-of-Possession) | RFC 8800 |
| OIDC (basic ID Token handling) | OpenID Connect Core 1.0 |

The module focuses on client concerns: building/redirecting authorization requests, exchanging codes for tokens, refreshing tokens, storing authorized client state, and providing pluggable HTTP integrations for token / user info retrieval.

## 2. Module responsibilities

- Provide a model for an authorized client (e.g. `OAuth2AuthorizedClient`) that ties together a `RegisteredClient`/client registration, access token, refresh token, and principal information.
- Implement request builders for the authorization redirect (including PKCE support), token request/response handling, and device flow helpers.
- Offer repositories/adapters to persist `OAuth2AuthorizedClient` instances across requests (in-memory, session, JDBC, or custom repositories).
- Integrate with HTTP clients to add authorization headers (Bearer or DPoP) for outgoing requests.
- Expose extension points for customizing token response handling, token refresh strategies, and token-based HTTP clients.

## 3. Core concepts and classes

- `ClientRegistration` — client metadata (client_id, client_secret, redirect_uris, authorizationGrantTypes, scopes, provider configuration)
- `OAuth2AuthorizedClient` — the runtime record holding `ClientRegistration`, `principalName`, `OAuth2AccessToken`, `OAuth2RefreshToken` and optionally `OidcIdToken`/user info
- `OAuth2AuthorizedClientRepository` — SPI to persist/load `OAuth2AuthorizedClient` (session-based, JWT-backed, JDBC implementations are common)
- `OAuth2AuthorizationRequest` / `OAuth2AuthorizationRequestResolver` — builds the authorization redirect URL (supports PKCE generation)
- `OAuth2AccessTokenResponseClient` — handles token endpoint exchange (authorization_code → tokens, refresh_token → tokens)
- `OAuth2AuthorizedClientManager` — high-level component that can obtain and refresh authorized clients for use by the application (coordinates repositories, providers, and token clients)
- `OAuth2AuthorizedClientService` (or `Repository`) — convenience service for storing and retrieving authorized clients
- `OAuth2DeviceAuthorizationRequest` + helpers — device flow helpers for issuing device codes and polling
- HTTP integration adapters / filters — components that intercept outbound requests and inject Authorization headers (Bearer / DPoP) using `OAuth2AuthorizedClient` data

## 4. Configuration model and extension points

- Applications create `ClientRegistration` records (or load them from properties/configuration) describing each OAuth2 provider.
- Provide or configure an `OAuth2AuthorizedClientRepository` implementation appropriate for the app (session repository for web apps, JDBC for server-side persistence, in-memory for tests).
- Customize `OAuth2AccessTokenResponseClient` to change how the token endpoint is called (e.g., to add custom headers, client authentication methods, or to support DPoP proofs).
- Provide `OAuth2AuthorizedClientManager` or use framework-provided ones to centralize token acquisition/refresh logic; register custom `OAuth2AuthorizationGrantRequestEntityConverter`/`ResponseHandlers` if needed.

## 5. Common flows

Below are simplified sequences for the most common flows. Sequence diagrams use a minimal textual description and example HTTP messages for clarity.

### 5.1 Authorization Code Grant with PKCE (typical browser-based client)

1. Client constructs authorization request with PKCE (`code_challenge`, `code_challenge_method=S256`) and redirects user-agent to the authorization endpoint:

GET /oauth2/authorize?response_type=code&client_id=...&redirect_uri=...&scope=openid%20email&state=xyz&code_challenge=...&code_challenge_method=S256

2. User authenticates and consents at the authorization server; AS redirects back to `redirect_uri` with `code` and `state`.

3. Client receives the callback, validates `state`, and exchanges the authorization code at the token endpoint (including `code_verifier`):

POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=...&redirect_uri=...&client_id=...&code_verifier=...

4. AS returns access token (and optionally refresh token, id_token):

{
  "access_token":"...",
  "token_type":"Bearer",
  "expires_in":3600,
  "refresh_token":"...",
  "id_token":"..."
}

5. Client persists an `OAuth2AuthorizedClient` record (via `OAuth2AuthorizedClientRepository`) for future requests. Use `OAuth2AuthorizedClientManager` to perform automatic refresh when needed.

Notes:
- PKCE: public clients must use PKCE; the client stores the code_verifier until token exchange.
- Validate `state` to prevent CSRF.

### 5.2 Client Credentials Grant (machine-to-machine)

1. Client (confidential) requests a token directly at the token endpoint:

POST /oauth2/token
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=resource.read

2. AS returns an access token (no refresh token in typical client credentials flows):

{ "access_token":"...","token_type":"Bearer","expires_in":3600 }

3. The client stores the token (e.g., in-memory) and uses it for outgoing requests, optionally using `OAuth2AuthorizedClientManager` to refresh when expired.

### 5.3 Device Authorization Grant (Device Flow)

1. Device requests device_code:

POST /oauth2/device_authorization
Content-Type: application/x-www-form-urlencoded

client_id=...&scope=openid%20email

2. AS returns `device_code`, `user_code`, `verification_uri`, `expires_in`, `interval`.
3. Device displays `user_code` and verification instructions; user completes authorization on a secondary device.
4. Device polls token endpoint with `device_code` until it receives tokens or a terminal error (see RFC 8628). The module exposes helpers to orchestrate this flow and to persist the resulting `OAuth2AuthorizedClient`.

### 5.4 Refresh Token Grant

1. When the access token is expired, client sends:

POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token=...

2. AS returns a fresh access token (and optionally a new refresh_token). Update the stored `OAuth2AuthorizedClient` accordingly.

Notes:
- Respect `refresh_token` reuse policies and rotate tokens when the server returns a new refresh token.

## 6. Token usage patterns (Bearer vs DPoP)

- Bearer tokens: include `Authorization: Bearer <access_token>` on outbound requests.
- DPoP: when DPoP is used, the client must produce a DPoP proof for each request (a signed JWT tied to the HTTP method + URI) and send it in the `DPoP` header; access tokens bound to DPoP require the token client to mint a key pair and include the JWK thumbprint in the token request.

The module provides adapters to attach either Bearer or DPoP proofs to outbound requests and to obtain DPoP-bound access tokens from token endpoints.

## 7. Storage / repository recommendations

- Web applications (browser-based): `OAuth2AuthorizedClientRepository` backed by the HTTP session or a cookie-backed store is common.
- Server-side apps: persist authorized clients in a database (JDBC) or cache with appropriate encryption for long-lived refresh tokens.
- CLI/device backends: store tokens securely on disk or use platform-provided secret storage.

Security: encrypt persisted refresh tokens, limit scope of stored tokens, and rotate/expire tokens according to server guidance.

## 8. Integration with Spring Security filter chain (notes)

- For web apps, the client support often integrates with authentication/authorization filters that initiate the authorization redirect and handle the callback, persisting the authorized client as part of the authenticated principal session.
- Outbound HTTP clients (RestTemplate / WebClient / custom) should use provided interceptors/adapters that obtain an `OAuth2AuthorizedClient` and inject the appropriate credentials (Bearer/DPoP) into the request.

## 9. Extension points for customization

- `ClientRegistration` loader — supply dynamic client registrations if needed.
- `OAuth2AccessTokenResponseClient` — customize token exchange behavior (client auth, headers, DPoP handling).
- `OAuth2AuthorizedClientRepository` — change persistence strategy.
- `OAuth2AuthorizedClientManager` customization — change refresh strategy, clock skew handling, and error handling policies.
- Outbound HTTP adapters — replace or extend the DPoP proof generation or header injection behavior.

## 10. References and further reading

- RFC 6749 — OAuth 2.0
- RFC 7636 — Proof Key for Code Exchange (PKCE)
- RFC 8628 — Device Authorization Grant
- RFC 8800 — DPoP
- OpenID Connect Core 1.0

If you want, I can also:
- Search the repo and list the exact classes that implement these client primitives (e.g. `OAuth2AuthorizedClient`, `OAuth2AuthorizedClientRepository`, token response clients), or
- Create a minimal example (small web app) using `ClientRegistration`, an `OAuth2AuthorizedClientRepository`, and the Authorization Code + PKCE flow to demonstrate end-to-end behavior.

