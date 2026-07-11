# Real-World OAuth2 Mapping — "Login with Google" ↔ Local Demo

This document maps the familiar **"Sign in with Google"** flow to the local `oauth2-demo` workflow, component by component and step by step. The OAuth2 protocol is identical — only the actors and URLs differ.

---

## Component Mapping

| Real-World (Google)               | Demo Equivalent                                        | Demo Config / Code                               |
|-----------------------------------|--------------------------------------------------------|--------------------------------------------------|
| **Google Accounts** (user store)  | `InMemoryUserDetailsManager` (local users)             | `SecurityConfig.java` — `users()` bean           |
| **Google Authorization Server**   | `AuthorizationServerConfig` (Spring AS)                | `AuthorizationServerConfig.java`                 |
| **Google's JWK Set**              | Local RSA-2048 key pair via `JWKSource`                 | `AuthorizationServerConfig.java` — `jwkSource()` |
| **Google's OAuth2 Client UI** (consent screen) | Spring AS built-in consent page (requires `requireAuthorizationConsent=true`) | `ClientSettings.builder().requireAuthorizationConsent(true)` |
| **Third-Party Website** (e.g., myapp.com) | React frontend (`http://localhost:3000`)           | `frontend/src/App.js`                            |
| **Third-Party's Backend**         | Spring Boot backend (`http://localhost:8085`)          | `OAuth2DemoApplication.java`                     |
| **Google's UserInfo Endpoint**    | Demo's `/user` + `/admin` endpoints                    | `DemoController.java`                            |
| **OAuth2 Client Registration** (Google Cloud Console) | `RegisteredClient` seeded in H2 DB               | `AuthorizationServerConfig.java` — `registeredClientRepository()` |
| **Google's Token Endpoint**       | `POST http://localhost:8085/oauth2/token`              | `OAuth2AuthorizationServerConfigurer`            |
| **Google's Auth Endpoint**        | `GET http://localhost:8085/oauth2/authorize`           | `OAuth2AuthorizationServerConfigurer`            |
| **Redirect URI** (Google Console) | `http://localhost:3000/callback` (frontend) or `http://localhost:8085/login/oauth2/code/demo-client` (backend) | `App.js` line 21, `AuthorizationServerConfig.java` lines 77–78 |
| **PKCE** (recommended for public clients) | PKCE `S256` — code verifier in `sessionStorage`, SHA-256 challenge | `App.js` — `generatePKCE()`                     |

---

## Step-by-Step Flow Mapping

### Step 0: Prerequisites — Registering the Client

| Real-World (Google) | Demo |
|---------------------|------|
| Developer goes to **Google Cloud Console** → APIs & Services → Credentials → **Create OAuth 2.0 Client ID** | The `RegisteredClient` is **seeded programmatically** at startup in `AuthorizationServerConfig.registeredClientRepository()`. No external console needed. |
| Fields: Client ID, Client Secret, Authorized Redirect URIs, Scopes | Fields: `.clientId("demo-client")`, `.clientSecret(passwordEncoder.encode("secret"))`, `.redirectUri("http://localhost:3000/callback")`, `.scope("openid", "profile")` |

---

### Step 1: User Clicks "Sign In"

| Real-World (Google) | Demo |
|---------------------|------|
| User clicks **"Sign in with Google"** on a third-party website | User clicks **"Login with OAuth2"** button in the React frontend |
| The website constructs the authorization URL with `client_id`, `redirect_uri`, `scope`, and PKCE params | `App.js` → `handleLogin()` generates PKCE challenge and constructs: |
| | ```
https://accounts.google.com/o/oauth2/v2/auth?
  response_type=code&
  client_id=738...apps.googleusercontent.com&
  redirect_uri=https://myapp.com/callback&
  scope=openid%20profile%20email&
  code_challenge=E9...&
  code_challenge_method=S256&
  state=abc123
``` |
| | ↓ **Analogous demo URL:** |
| | ```
http://localhost:8085/oauth2/authorize?
  response_type=code&
  client_id=demo-client&
  redirect_uri=http://localhost:3000/callback&
  scope=openid%20profile&
  code_challenge=E9...&
  code_challenge_method=S256&
  state=random-state-xyz
``` |

---

### Step 2: User Authenticates

| Real-World (Google) | Demo |
|---------------------|------|
| Google displays its **login page** — user enters their Gmail address and password | The Spring Authorization Server displays the built-in **form login page** at `http://localhost:8085/login` |
| Google validates credentials against **Google Accounts** (millions of users, MFA, security features) | `SecurityConfig.users()` validates against `InMemoryUserDetailsManager` with **2 hardcoded users**: |
| | | Username | Password | Role |
| | |----------|----------|------|
| | | `user` | `password` | `ROLE_USER` |
| | | `admin` | `admin` | `ROLE_ADMIN` |

> **Key difference:** Google uses a massive distributed identity platform. The demo uses a simple in-memory store — but **the OAuth2 protocol is identical**. You could swap in a `JdbcUserDetailsManager` or an LDAP store without changing any OAuth2 logic.

---

### Step 3: User Consents to Scopes

| Real-World (Google) | Demo |
|---------------------|------|
| Google shows a **consent screen**: *"myapp.com wants to: View your email address, View your profile info"* | The Spring AS shows a **consent page** (because `requireAuthorizationConsent=true` in `ClientSettings`): |
| ![Google Consent](https://...consent.png) | ```
/oauth2/authorize consent page:
  "Demo Client requests the following scopes:
   ☐ openid  ☐ profile"
``` |
| User clicks **"Allow"** | User clicks **"Submit Consent"** |
| Scopes granted: `openid`, `profile`, `email` | Scopes granted: `openid`, `profile` |

> **Note:** Google often **skips** the consent screen for trusted apps where the user already consented. That's controlled by the `requireAuthorizationConsent` setting — set it to `false` to mirror Google's behaviour for previously-authorized clients.

---

### Step 4: Authorization Code Issued

| Real-World (Google) | Demo |
|---------------------|------|
| Google redirects the browser back to the third-party site: | The Spring AS redirects back to the frontend: |
| ```
302 Redirect
Location: https://myapp.com/callback?
  code=4/0AX...&
  state=abc123
``` |
| The `code` is a **one-time-use authorization code** bound to the PKCE challenge | Same — the code is stored in the `oauth2_authorization` table (H2) and bound to the `code_challenge` |
| The `state` parameter prevents CSRF attacks on the redirect | Same — the demo sends `state=random-state-...` |

---

### Step 5: Token Exchange (Back-Channel)

| Real-World (Google) | Demo |
|---------------------|------|
| The third-party **backend** makes a server-to-server POST to Google: | The React frontend makes a **direct** POST from the browser (CORS-enabled): |
| ```
POST https://oauth2.googleapis.com/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=4/0AX...&
redirect_uri=https://myapp.com/callback&
client_id=738...apps.googleusercontent.com&
client_secret=GOCSPX-...&   ← confidential client
code_verifier=dBj...        ← PKCE
``` |
| |
| ↓ **Analogous demo request:** |
| ```
POST http://localhost:8085/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=abc123...&
redirect_uri=http://localhost:3000/callback&
client_id=demo-client&
client_secret=secret&
code_verifier=dBj...
``` |

| Real-World (Google) | Demo |
|---------------------|------|
| Google validates the `code_verifier` against the original `code_challenge` (PKCE) | Spring AS validates the PKCE proof — same algorithm (`S256`) |
| Google responds with tokens: | Spring AS responds with tokens: |
| ```json
{
  "access_token": "ya29.a0...",
  "expires_in": 3600,
  "token_type": "Bearer",
  "scope": "openid profile email",
  "refresh_token": "1//0g...",
  "id_token": "eyJhbG..."
}
``` |
| The `access_token` is opaque to Google (could be JWT or reference token) | The demo's `access_token` is a **signed JWT** (RS256 with the local RSA key) |
| The `id_token` is a **JWT** containing user claims (`sub`, `name`, `email`, `picture`) | The `id_token` is a **JWT** containing `sub` (user's login name), `iss` (http://localhost:8085), `aud` (demo-client) |

> **Key difference:** Google's token exchange happens **server-side** (the third-party backend calls Google). In this demo, it happens **client-side** (the React app calls directly) for simplicity. A production setup would proxy through the backend.

---

### Step 6: Accessing Protected Resources

| Real-World (Google) | Demo |
|---------------------|------|
| The third-party backend calls Google APIs with the access token: | The frontend (or backend) calls the demo's protected endpoints: |
| ```
GET https://www.googleapis.com/oauth2/v2/userinfo
Authorization: Bearer ya29.a0...
``` |
| | ↓ **Analogous demo request:** |
| | ```
GET http://localhost:8085/user
Authorization: Bearer eyJhbG...
``` |
| Google returns user info: | Demo returns a simple greeting: |
| ```json
{
  "id": "12345",
  "email": "user@gmail.com",
  "name": "John Doe",
  "picture": "https://..."
}
``` |
| The third-party **trusts** this response because it validated the token against Google's JWKs | The demo validates the JWT signature against the local JWK Set at `/oauth2/jwks` |

---

### Step 7: Optional — Token Refresh

| Real-World (Google) | Demo |
|---------------------|------|
| When the access token expires (3600s), the backend uses the `refresh_token` to get a new one: | The demo supports `REFRESH_TOKEN` grant type — the `OAuth2FlowExample.java` includes it in its provider chain: |
| ```
POST https://oauth2.googleapis.com/token
grant_type=refresh_token&
refresh_token=1//0g...&
client_id=738...apps.googleusercontent.com
``` | ```java
OAuth2AuthorizedClientProviderBuilder.builder()
    .authorizationCode()
    .refreshToken()   // ← enabled
    .clientCredentials()
    .build();
``` |

---

## Token Comparison

### Access Token (JWT Payload)

| Claim | Google Example                               | Demo Example                                        |
|-------|----------------------------------------------|-----------------------------------------------------|
| `iss` | `https://accounts.google.com`                | `http://localhost:8085`                              |
| `sub` | `1234567890` (Google account ID)             | `user` (the login username)                          |
| `aud` | `738...apps.googleusercontent.com`           | `demo-client`                                        |
| `exp` | Unix timestamp (1 hour)                      | Unix timestamp (1 hour — configured via `TokenSettings.accessTokenTimeToLive(Duration.ofHours(1))`) |
| `iat` | Unix timestamp (now)                         | Unix timestamp (now)                                  |
| `scope` | `openid profile email`                     | `openid profile`                                     |

### ID Token (JWT Payload)

| Claim | Google Example                               | Demo Example                                        |
|-------|----------------------------------------------|-----------------------------------------------------|
| `iss` | `https://accounts.google.com`                | `http://localhost:8085`                              |
| `sub` | `1234567890` (Google account ID)             | `user` (the login username)                          |
| `aud` | `738...apps.googleusercontent.com`           | `demo-client`                                        |
| `azp` | `738...apps.googleusercontent.com`           | `demo-client`                                        |
| `email` | `user@gmail.com`                          | _(not present — no `email` scope configured)_        |
| `name` | `John Doe`                                 | _(not present — profiles depend on UserDetails)_     |

---

## Security Comparisons

| Aspect | Real-World (Google) | Demo |
|--------|---------------------|------|
| **Token Signing** | Google uses **publicly known** RSA keys rotated periodically | RSA-2048 key generated **at each startup** — existing tokens invalidated on restart |
| **PKCE** | Required for all public clients (since 2021) | `S256` enforced — frontend implements full PKCE |
| **Client Secret** | Stored in server config, never exposed to browser | `demo-client` uses `CLIENT_SECRET_BASIC`; the frontend embeds it for simplicity (would be backend-only in production) |
| **TLS/HTTPS** | **Required** — all endpoints enforce HTTPS | **HTTP** — localhost demo only. In production, TLS is mandatory |
| **User Store** | Google Accounts — billions of users, MFA, security monitoring | `InMemoryUserDetailsManager` — 2 users, no MFA |
| **Consent** | Shown once, remembered per client | Shown every time (`requireAuthorizationConsent=true`) — can be changed to `false` to mirror Google's behaviour |
| **Token Format** | Opaque access tokens + JWT ID tokens | JWT access tokens + JWT ID tokens (both RS256) |
| **Refresh Tokens** | Long-lived, can be revoked by user | Supported but expires when H2 resets on restart |

---

## What Would Need to Change to Be "Production-Like"

To make the demo behave like a real Google-style identity provider:

| Change | Current Demo | Production Equivalent |
|--------|-------------|----------------------|
| User store | `InMemoryUserDetailsManager` | Database-backed (`JdbcUserDetailsManager`) or LDAP |
| Key management | Key generated at startup | Persistent key store (e.g., JWK Set loaded from file/Vault) |
| HTTPS | Plain HTTP | TLS certificate |
| Client registration | Hardcoded in `@Bean` | Database table or admin UI |
| Token exchange | Client-side (React → AS) | Server-side (backend → AS) with client secret protected |
| Consent persistence | In-memory H2 | Persistent database with consent expiry |
| User info endpoint | Custom `/user` controller | Standard `userinfo` endpoint as per OIDC spec |
| Session management | Single-node | Distributed session store (Redis, JDBC) |

---

## Summary

```
Real World:                         Demo:
─────────────────────────────       ─────────────────────────────
Google Accounts             ───     InMemoryUserDetailsManager
Google Authorization Server ───     AuthorizationServerConfig + SecurityConfig
Third-Party Website         ───     React Frontend (localhost:3000)
Third-Party Backend         ───     Spring Boot Backend (localhost:8085)
Google APIs / UserInfo      ───     DemoController (/user, /admin)
Google Cloud Console        ───     registeredClientRepository() @Bean
PKCE (S256)                 ───     generatePKCE() in App.js
JWT (RS256 signed)          ───     Local RSA-2048 JWKSource
```

The **OAuth2 protocol flow is identical** in both cases. The only differences are:
- **Scale**: Google serves billions; this demo serves one developer
- **URLs**: `accounts.google.com` → `localhost:8085`
- **Users**: Google Accounts → 2 hardcoded test users
- **Keys**: Google's managed PKI → a one-line RSA key generator

This is why OAuth2 is powerful — **the same code that integrates with Google's AS can integrate with a local Spring AS** by changing only the `ClientRegistration` properties (issuer URI, client ID, client secret).
