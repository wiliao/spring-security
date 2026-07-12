# Fix: Token Exchange & ID Token Parsing Errors

## Problems

Two distinct errors were encountered during the OAuth2 Authorization Code + PKCE flow:

### Error 1 — Network Error (CORS)

```
Error: Token exchange failed: Network Error
```

The React frontend (running on `http://localhost:3000`) made an AJAX POST request to the backend token endpoint (`http://localhost:8085/oauth2/token`) using an absolute URL. This constituted a **cross-origin request** that was blocked by the browser because the backend sent no CORS headers.

### Error 2 — JSON Parse Error (ID Token Decoding)

```
Error: Token exchange failed: Unexpected non-whitespace character after JSON at position 3
```

The token exchange **succeeded**, but the subsequent ID token payload decoding crashed. The error was misleading: because it occurred in the same `try-catch` block, it was reported as a token exchange failure.

---

## Root Cause Analysis

### Error 1: CORS / Network Error

The React app's `package.json` configured a dev server proxy (`"proxy": "http://localhost:8085"`), which forwards requests with **relative** URLs to the backend on port 8085. However, the frontend code used **absolute** URLs:

```javascript
const tokenEndpoint = 'http://localhost:8085/oauth2/token';   // absolute — bypasses proxy
const resourceEndpoint = 'http://localhost:8085/user';         // absolute — bypasses proxy
```

The CRA dev server proxy only intercepts requests that don't match an `Accept: text/html` header. Absolute URLs bypass it entirely, triggering a direct browser-to-backend cross-origin request. Since the backend had no CORS configuration, the browser blocked the response.

### Error 2: `Base64.toUint8Array()` + `JSON.parse()` Mismatch

The JWT payload decoding used:

```javascript
const payload = JSON.parse(Base64.toUint8Array(parts[1]));
```

`Base64.toUint8Array()` (from the `js-base64` library) returns a **`Uint8Array`** object, **not a string**. When `JSON.parse()` receives a non-string argument, JavaScript coerces it via `.toString()`. For a `Uint8Array`, `.toString()` produces a **comma-separated list of byte values**, e.g.:

```
"255,34,56,77,111,99,107,..."
```

`JSON.parse("255,34,56,...")` successfully parses the first number (`255`, 3 characters) then encounters `,` at position 3 — producing:

> **Unexpected non-whitespace character after JSON at position 3**

---

## Solution

### Fix 1: Backend CORS Configuration

Added a `CorsConfigurationSource` bean to `AuthorizationServerConfig.java` and enabled `.cors()` on both security filter chains:

**`AuthorizationServerConfig.java`** — New bean:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/oauth2/**", configuration);
    source.registerCorsConfiguration("/.well-known/**", configuration);
    source.registerCorsConfiguration("/user", configuration);
    return source;
}
```

**`AuthorizationServerConfig.java`** — CORS enabled on AS chain:
```java
http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
    .with(authorizationServerConfigurer, Customizer.withDefaults())
    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
    .cors(Customizer.withDefaults())            // ← added
    .formLogin(Customizer.withDefaults());
```

**`SecurityConfig.java`** — CORS enabled on default chain:
```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers(...)
        .permitAll()
        .anyRequest().authenticated())
    .formLogin(Customizer.withDefaults())
    .oauth2Login(Customizer.withDefaults())
    .cors(Customizer.withDefaults());            // ← added
```

### Fix 2: Correct ID Token Payload Parsing

Changed the JWT payload decoding from `Base64.toUint8Array()` to `Base64.decode()`, which returns a proper UTF-8 string:

**`frontend/src/App.js`**:
```javascript
// Before (broken):
const payload = JSON.parse(Base64.toUint8Array(parts[1]));

// After (fixed):
const payload = JSON.parse(Base64.decode(parts[1]));
```

**`frontend/src/components/TokenDisplay.js`** — same fix in the `decodeToken()` helper function.

---

## Changes Summary

| File | Change |
|------|--------|
| `src/main/java/.../config/AuthorizationServerConfig.java` | Added `CorsConfigurationSource` bean, activated `.cors()` on AS filter chain |
| `src/main/java/.../config/SecurityConfig.java` | Activated `.cors()` on default filter chain |
| `frontend/src/App.js` | Switched `Base64.toUint8Array()` → `Base64.decode()` for JWT payload parsing |
| `frontend/src/components/TokenDisplay.js` | Same `Base64` fix for the token display component |

---

## Verification

1. **Start the backend:**
   ```bash
   cd C:\Samples-07-spring-security\spring-security-fork
   .\gradlew :oauth2-demo:bootRun
   ```

2. **Start the frontend:**
   ```bash
   cd C:\Samples-07-spring-security\spring-security-fork\oauth2\oauth2-demo\frontend
   npm start
   ```

3. **Open `http://localhost:3000`** and click "Login with OAuth2"

4. **Expected result:**
   - Redirected to the Authorization Server login page at `localhost:8085`
   - Log in with `user` / `password`
   - Approve consent for `openid` and `profile` scopes
   - Redirected back to `localhost:3000/callback?code=...`
   - Token exchange succeeds silently
   - ID token and access token are displayed in the Tokens tab
   - Protected Resource tab can call the `/user` endpoint
   - Database Viewer tab shows the `oauth2_registered_client`, `oauth2_authorization`, and `oauth2_authorization_consent` tables

---

## Key Lessons

1. **`Base64.toUint8Array()` vs `Base64.decode()`**: `toUint8Array()` returns a typed array of raw bytes — useful for binary data or `crypto.subtle` operations, but **not** for decoding JSON payloads. Use `Base64.decode()` when the result is expected to be a UTF-8 string (like a JWT payload).

2. **CRA proxy only intercepts relative URLs**: The `"proxy"` setting in `package.json` only forwards requests with paths (e.g., `/oauth2/token`). Absolute URLs with hostnames (e.g., `http://localhost:8085/oauth2/token`) bypass the proxy and become cross-origin requests.

3. **CORS credentials**: When `allowCredentials(true)` is set, `allowedOrigins` must be an explicit list — wildcard `*` is not permitted. The origin must match exactly (including scheme and port).

