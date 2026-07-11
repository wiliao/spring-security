# oauth2-demo — Complete OAuth2 Authorization Code + PKCE Demo

Complete demo showing how the `oauth2` modules in this repository can be wired together into a fully functional OAuth2 Authorization Server with a modern ReactJS frontend that demonstrates the Authorization Code Grant with PKCE flow.

The demo includes:
- **Spring Boot Backend**: Authorization Server with embedded H2 database, resource endpoints, and API viewers
- **ReactJS Frontend**: Interactive UI implementing PKCE, token display, protected resource access, and database viewer
- **Educational Focus**: Clear explanations and examples for learning OAuth2 and PKCE

## Quick Start

### 1. Start the Backend (Spring Boot)

From the repository root (ensure `include 'oauth2-demo'` is in `settings.gradle`):

```powershell
# Windows PowerShell
.\gradlew.bat :oauth2-demo:bootRun --no-daemon

# Unix/Linux
./gradlew :oauth2-demo:bootRun
```

The backend runs on **http://localhost:8085** with:
- Authorization Server endpoints (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`)
- Protected resources (`/user`, `/admin`)
- H2 console (`/h2-console`)
- Database viewer API (`/db/clients`, `/db/authorizations`, `/db/consents`)

### 2. Start the Frontend (ReactJS)

In a new terminal:

```bash
cd oauth2/oauth2-demo/frontend
npm install
npm start
```

The frontend runs on **http://localhost:3000** with:
- Authorization flow initiator (PKCE-enabled)
- JWT token decoder
- Protected resource caller (Bearer token)
- Database viewer UI
- H2 console link

### 3. Test the Flow

1. Open http://localhost:3000
2. Click "Login with OAuth2"
3. Log in: username `user`, password `password`
4. Approve consent
5. View tokens, call protected resources, inspect database

## Demo Credentials

- **User**: `user` / `password` (role: USER)
- **Admin**: `admin` / `admin` (role: ADMIN)

## Demo Client Configuration

- **Client ID**: `demo-client`
- **Client Secret**: `secret`
- **Redirect URIs**: http://localhost:8085/login/oauth2/code/demo-client, http://localhost:3000/callback
- **Scopes**: `openid`, `profile`
- **PKCE**: Required

## What's Included

### Backend

- OAuth2 Authorization Server (RFC 6749 + PKCE)
- Embedded H2 in-memory database
- JDBC-backed client and authorization storage
- JWK Set generation for token signing
- Protected resource endpoints
- Database viewer REST API
- H2 web console

### Frontend

- PKCE implementation (SHA-256 code challenge/verifier)
- Full authorization code flow
- JWT token decoder and viewer
- Protected resource caller with Bearer token
- Interactive database viewer
- Responsive React UI

## API Endpoints

**Authorization Server:**
- `GET /oauth2/authorize` — Authorization endpoint
- `POST /oauth2/token` — Token endpoint
- `GET /oauth2/jwks` — JWK Set endpoint
- `GET /.well-known/oauth-authorization-server` — Discovery

**Resource Server:**
- `GET /user` — Protected resource (Bearer token required)
- `GET /admin` — Admin resource

**Database Viewer:**
- `GET /db/clients` — Registered clients
- `GET /db/authorizations` — Authorizations
- `GET /db/consents` — User consents

**H2 Console:**
- `GET /h2-console` — Web console (JDBC: `jdbc:h2:mem:testdb`)

## Architecture & Documentation

See `docs/oauth2-demo-architecture-and-workflow.md` for detailed architecture, wiring, and troubleshooting.

See `frontend/README.md` for frontend-specific instructions and PKCE details.

## Notes

- Educational use only; not suitable for production
- Runs on same machine (Authorization Server + client frontend)
- In-memory H2 database (resets on restart)
- RSA keys regenerated each startup
- PKCE fully implemented for secure public client flows

## References

- [RFC 6749 — OAuth 2.0](https://tools.ietf.org/html/rfc6749)
- [RFC 7636 — PKCE](https://tools.ietf.org/html/rfc7636)
- [Spring Authorization Server](https://spring.io/projects/spring-authorization-server)
- [React Documentation](https://react.dev)

