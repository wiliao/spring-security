# Fix: Protected Resource Access (Bearer Token Authentication)

## Problem

After successful token exchange, clicking "Call Protected Resource" on the React frontend returned:

```
Error: Error: Network Error
```

The backend server log showed:

```
2026-07-12T10:15:12.838-04:00 DEBUG ... AnonymousAuthenticationFilter  : Set SecurityContextHolder to anonymous SecurityContext
2026-07-12T10:15:12.843-04:00 DEBUG ... DefaultRedirectStrategy        : Redirecting to /login
```

## Root Cause

The `GET /user` request from the Protected Resource tab sends an `Authorization: Bearer <access_token>` header. However, the **default security filter chain had no `BearerTokenAuthenticationFilter`** to process it.

The existing filter chain only registered:
- `UsernamePasswordAuthenticationFilter` — handles form login (`POST /login` with username/password)
- `OAuth2LoginAuthenticationFilter` — handles the interactive OAuth2 login callback

The Bearer token was **silently ignored**, causing the request to fall through to `AnonymousAuthenticationFilter`. The `AuthorizationFilter` then rejected the anonymous user for the authenticated `/user` endpoint and issued a **302 redirect to `/login`** — which the `axios` HTTP client interprets as a Network Error (since it doesn't follow redirects across different content types).

## Solution

Enable OAuth2 Resource Server support with JWT decoder on the default security filter chain.

**`SecurityConfig.java`** — added `.oauth2ResourceServer()`:
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/", "/error", "/webjars/**", "/login/**", "/oauth2/**", "/.well-known/**",
            "/h2-console/**", "/db/**")
    .permitAll()
    .anyRequest()
    .authenticated())
    .formLogin(Customizer.withDefaults())
    .oauth2Login(Customizer.withDefaults())
    // Enable OAuth2 Resource Server with JWT decoder so that API calls
    // with a Bearer token (e.g. from the React frontend's ProtectedResource
    // tab) are authenticated via the existing JwtDecoder bean.
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
    // Enable CORS so the React frontend (localhost:3000) can access protected resources
    .cors(Customizer.withDefaults());
```

This registers a `BearerTokenAuthenticationFilter` that:
1. Extracts the `Authorization: Bearer <token>` header from incoming requests
2. Validates the token using the existing `JwtDecoder` bean (configured with `http://localhost:8085/oauth2/jwks`)
3. Sets an authenticated `JwtAuthenticationToken` in the `SecurityContext`
4. Allows the request to proceed past `AuthorizationFilter`

## The JwtDecoder (Already Configured)

The `JwtDecoder` bean was already present in `SecurityConfig.java`:
```java
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri("http://localhost:8085/oauth2/jwks").build();
}
```

No changes were needed here — `.oauth2ResourceServer().jwt()` auto-detects this bean.

## Files Changed

| File | Change |
|------|--------|
| `src/main/java/.../config/SecurityConfig.java` | Added `.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))` to the default security filter chain |

## Verification

1. Start the backend: `.\gradlew :oauth2-demo:bootRun`
2. Start the frontend: `npm start` (in `frontend/` directory)
3. Open `http://localhost:3000`
4. Complete the full OAuth2 login flow (authorize → login → consent → callback)
5. Switch to the **Protected Resource** tab
6. Click **"Call Protected Resource"**
7. Expected response (JSON):
   ```json
   "Hello, user — this is a protected resource."
   ```
   or equivalent showing the authenticated principal name from the JWT.

