# oauth2-demo — Complete OAuth2 Authorization Code + PKCE Demo

Complete demo showing how the `oauth2` modules in this repository can be wired together into a fully functional OAuth2 Authorization Server with a modern ReactJS frontend that demonstrates the Authorization Code Grant with PKCE flow.

> ⚠️ **Important:** This is a **sample module** inside the Spring Security monorepo, not a standalone app. It builds against the local SNAPSHOT source modules (Spring Boot `4.1.0-SNAPSHOT` / Spring Security `7.1.1-SNAPSHOT`), not published Maven artifacts. See [Build Status](#build-status) below for known issues.

The demo includes:
- **Spring Boot Backend**: Authorization Server with embedded H2 database, resource endpoints, and API viewers
- **ReactJS Frontend**: Interactive UI implementing PKCE, token display, protected resource access, and database viewer
- **Educational Focus**: Clear explanations and examples for learning OAuth2 and PKCE

## Prerequisites

- **Java 17+** (bytecode target is Java 17 regardless of your JDK version)
- **Node.js 14+** and **npm** (for the frontend)
- The `oauth2-demo` module must be registered in the root `settings.gradle`:
  ```groovy
  include 'oauth2-demo'
  ```
  > This is **not yet present** in the fork's `settings.gradle`. Add it manually before running any Gradle commands.

## Quick Start

### 1. Start the Backend (Spring Boot)

From the repository root:

```bash
# Fix formatting before the first build (required by repo conventions)
./gradlew :oauth2-demo:format

# Start the app (preferred over bootJar — see Build Status)
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
2. Click **"Login with OAuth2"** — the app generates a PKCE challenge and redirects to the Authorization Server
3. Log in with username `user`, password `password`
4. Approve the requested scopes (openid, profile)
5. The frontend exchanges the authorization code for tokens (access, refresh, ID) and decodes the JWT
6. Use the token viewer, protected resource caller, and database inspector from the UI

## Demo Credentials

| Username | Password   | Role    |
|----------|------------|---------|
| `user`   | `password` | `ROLE_USER` |
| `admin`  | `admin`    | `ROLE_ADMIN` |

## Demo Client Configuration

| Property         | Value                                                                |
|------------------|----------------------------------------------------------------------|
| **Client ID**    | `demo-client`                                                        |
| **Client Secret** | `secret` (BCrypt-encoded in the database)                           |
| **Grant Types**  | `authorization_code`, `refresh_token`                                |
| **Scopes**       | `openid`, `profile`                                                  |
| **Redirect URIs** | `http://localhost:8085/login/oauth2/code/demo-client`<br>`http://localhost:8085/authorized` |
| **PKCE**         | `S256` (required by backend, implemented by frontend)                |

> **Note for frontend flow:** The frontend (port 3000) uses `http://localhost:3000/callback` as its redirect URI. To use the full PKCE flow from the browser, add this redirect URI to the `RegisteredClient` seed in `AuthorizationServerConfig.java`:
> ```java
> .redirectUri("http://localhost:3000/callback")
> ```

## What's Included

### Backend (`src/main/java/sample/oauth2/demo/`)

| Package | File | Purpose |
|---------|------|---------|
| _(root)_ | `OAuth2DemoApplication.java` | `@SpringBootApplication` entry point |
| `config/` | `AuthorizationServerConfig.java` | H2 data source, `RegisteredClientRepository` (JDBC), `OAuth2AuthorizationService`, `JWKSource`, AS `SecurityFilterChain` |
| `config/` | `SecurityConfig.java` | Default filter chain, in-memory users, `ClientRegistrationRepository`, `JwtDecoder`, `OAuth2AuthorizedClientRepository` |
| `config/` | `ResourceServerConfig.java` | Placeholder for resource server configuration |
| `client/` | `OAuth2FlowExample.java` | Example component demonstrating token obtention and protected resource calls |
| `web/` | `DemoController.java` | `GET /`, `/user`, `/admin` endpoints |
| `web/` | `DatabaseViewerController.java` | `GET /db/clients`, `/db/authorizations`, `/db/consents` |

### Frontend (`frontend/`)

| File | Purpose |
|------|---------|
| `src/App.js` | Main OAuth2 flow logic and PKCE handling |
| `src/components/AuthorizationFlow.js` | Login initiation screen |
| `src/components/TokenDisplay.js` | JWT token viewer and decoder |
| `src/components/ProtectedResource.js` | Call protected endpoints with Bearer token |
| `src/components/DatabaseViewer.js` | Fetch and display database tables |

## API Endpoints

**Authorization Server:**
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/oauth2/authorize` | Authorization endpoint (requires authentication + consent) |
| `POST` | `/oauth2/token` | Token endpoint (supports authorization_code and refresh_token grants) |
| `GET` | `/oauth2/jwks` | JWK Set endpoint (RSA-2048 public key) |
| `GET` | `/.well-known/oauth-authorization-server` | OpenID Discovery metadata |

**Protected Resources:**
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/user` | Protected resource — requires valid Bearer token |
| `GET` | `/admin` | Admin resource — requires valid Bearer token |

**Database Viewer:**
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/db/clients` | Registered OAuth2 clients from `oauth2_registered_client` table |
| `GET` | `/db/authorizations` | Authorizations from `oauth2_authorization` table |
| `GET` | `/db/consents` | User consents from `oauth2_authorization_consent` table |

**H2 Console:**
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/h2-console` | Web-based SQL console (JDBC URL: `jdbc:h2:mem:testdb`) |

## Build Status

| Command | Status | Notes |
|---------|--------|-------|
| `./gradlew :oauth2-demo:format` | ✅ Works | Run before every build to fix formatting |
| `./gradlew :oauth2-demo:build` | ⚠️ Partial | Compiles successfully; `bootJar` fails (see below) |
| `./gradlew :oauth2-demo:bootRun` | ✅ Works | **Preferred** way to run the application |

**Known issue — `bootJar` fails:**
```
NoSuchFieldError: TOOLS
```
This is a **genuine upstream bug** in the Boot `4.1.0-SNAPSHOT` Gradle plugin — it's thrown from inside `BootZipCopyAction$Processor.writeJarToolsIfNecessary`. The `bootRun` task works fine because it never touches the layered-jar tooling. See `docs/oauth2-demo-build-troubleshooting.md` (Issue 9) for the full investigation.

## Documentation

| Document | Description |
|----------|-------------|
| `docs/oauth2-demo-architecture-and-workflow.md` | Detailed architecture, wiring, and flow diagrams |
| `docs/oauth2-demo-build-troubleshooting.md` | Root-cause analysis of all 11 build issues encountered |
| `docs/project-folder-structure.md` | Full project hierarchy of the Spring Security monorepo |
| `BUILD_FIX_CHANGES.md` | Summary of changes applied to fix the build |
| `GRADLE_FIX_SUMMARY.md` | Technical root cause and fix summary |
| `frontend/README.md` | Frontend-specific instructions and PKCE implementation details |

## Architecture Overview

```
┌──────────────┐     PKCE Auth Request      ┌──────────────────────┐
│              │  ──────────────────────────▶  │                      │
│   Frontend   │     Authorization Code       │  Spring Boot (8085)   │
│   ReactJS    │  ◀──────────────────────────  │  ├─ Auth Server       │
│   (3000)     │                              │  ├─ Resource Server   │
│              │     Bearer Token Request     │  ├─ H2 Database       │
│              │  ──────────────────────────▶  │  └─ DB Viewer API    │
└──────────────┘     Protected Resource       └──────────────────────┘
```

The Authorization Server and Resource Server run in the same Spring Boot application. The H2 in-memory database stores registered clients, authorization grants, and user consents. The frontend implements the full PKCE (RFC 7636) flow — see `frontend/README.md` for details.

## Notes

- **Educational use only** — not suitable for production
- **In-memory H2 database** — data resets on every restart. The `RegisteredClient` is seeded at startup in `AuthorizationServerConfig.java`
- **RSA keys regenerated** each startup — existing tokens become invalid after restart
- **PKCE fully implemented** for secure public client flows (`S256` challenge method)
- **All-in-one** — the Authorization Server, Resource Server, and H2 database are a single Spring Boot application for demo simplicity

## References

- [RFC 6749 — OAuth 2.0](https://tools.ietf.org/html/rfc6749)
- [RFC 7636 — PKCE](https://tools.ietf.org/html/rfc7636)
- [Spring Authorization Server](https://spring.io/projects/spring-authorization-server)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [React Documentation](https://react.dev)

