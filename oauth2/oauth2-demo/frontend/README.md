# OAuth2 Demo Frontend — ReactJS UI for Authorization Code + PKCE Flow

This is a React-based frontend for the oauth2-demo module that demonstrates the OAuth2 Authorization Code Grant with PKCE (Proof Key for Code Exchange).

## Features

- **Authorization Code Flow with PKCE**: Interactive step-by-step OAuth2 flow
- **Token Display**: View and decode JWT tokens (access token, ID token)
- **Protected Resource Access**: Call protected endpoints using the access token as a Bearer token
- **Database Viewer**: Inspect registered clients, authorizations, and consents stored in the embedded H2 database
- **H2 Console Integration**: Direct SQL access to the demo database
- **Responsive UI**: Modern, intuitive interface with clear flow explanations

## Prerequisites

- Node.js 14+ and npm
- The Spring Boot oauth2-demo backend running on http://localhost:8085

## Installation and Running

```bash
# From the frontend folder (oauth2/oauth2-demo/frontend)

# Install dependencies
npm install

# Start the React development server (runs on http://localhost:3000)
npm start

# Build for production
npm build
```

## How It Works

1. **Click "Login with OAuth2"** to initiate the flow
2. **Generate PKCE Challenge**: The app generates a random code_verifier and derives a code_challenge (SHA-256)
3. **Redirect to Authorization Server**: You are redirected to http://localhost:8085/oauth2/authorize with PKCE parameters
4. **Authenticate**: Enter username `user` and password `password`
5. **Consent**: Approve the scopes (openid, profile)
6. **Token Exchange**: The app receives an authorization code and exchanges it for tokens using the stored code_verifier (PKCE verification)
7. **Access Protected Resource**: Use the access token to call `/user` endpoint with Bearer authentication
8. **View Database**: Inspect the H2 database contents (registered clients, authorizations, consents)

## Key Files

- `src/App.js` — Main OAuth2 flow logic and PKCE handling
- `src/components/AuthorizationFlow.js` — Login initiation screen
- `src/components/TokenDisplay.js` — JWT token viewer and decoder
- `src/components/ProtectedResource.js` — Call protected endpoints with access token
- `src/components/DatabaseViewer.js` — Fetch and display database tables
- `public/index.html` — HTML entry point

## PKCE Implementation

This frontend implements PKCE (RFC 7636) as follows:

1. **Code Verifier**: Generated as a random 32-byte value, base64url-encoded
2. **Code Challenge**: SHA-256 hash of the code verifier, base64url-encoded
3. **Authorization Request**: Include `code_challenge` and `code_challenge_method=S256`
4. **Token Exchange**: Include the original `code_verifier` when exchanging the authorization code for tokens

The Authorization Server validates that the `code_verifier` matches the `code_challenge` to ensure the request is legitimate.

## Credentials

- **Username**: `user`
- **Password**: `password`

Or use `admin` / `admin` for additional privileges.

## Troubleshooting

- **CORS errors**: Ensure the Spring Boot backend is running and has CORS configured (it should allow localhost:3000)
- **Token exchange fails**: Check the Authorization Server console for errors; ensure redirect URIs match exactly
- **Database viewer shows no data**: The data may not yet be created; try completing the authorization flow first
- **H2 console not accessible**: Visit http://localhost:8085/h2-console (use JDBC URL `jdbc:h2:mem:testdb`)

## Environment Configuration

To change the OAuth2 configuration (client_id, scopes, endpoint URLs), edit `src/App.js`:

```javascript
const clientId = 'demo-client';
const clientSecret = 'secret';
const redirectUri = 'http://localhost:3000/callback';
const authorizationEndpoint = 'http://localhost:8085/oauth2/authorize';
const tokenEndpoint = 'http://localhost:8085/oauth2/token';
const resourceEndpoint = 'http://localhost:8085/user';
```

## Production Build

To create a production build optimized for deployment:

```bash
npm run build
```

This generates a `build/` folder with optimized, minified code ready for serving.

## References

- [RFC 6749 — OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)
- [RFC 7636 — Proof Key for Public Clients (PKCE)](https://tools.ietf.org/html/rfc7636)
- [React Documentation](https://react.dev)
- [Axios HTTP Client](https://axios-http.com)

