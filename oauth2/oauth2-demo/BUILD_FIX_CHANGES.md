# OAuth2 Demo Build Fix — Changes Applied

## Issue Summary

The `oauth2/oauth2-demo` module failed to build against the current Spring Security fork (targeting **Spring Boot `4.1.0-SNAPSHOT`** / **Spring Security `7.1.1-SNAPSHOT`**). The demo was originally written against older, released APIs and was never updated to work inside this monorepo.

Eleven distinct issues were identified and fixed. This document summarises them; the detailed `docs/oauth2-demo-build-troubleshooting.md` has the full root-cause analysis for each one.

---

## Issues Fixed

| #  | Issue                                                                   | Status |
|----|-------------------------------------------------------------------------|--------|
| 1  | Wrong dependency coordinates (Maven Central versions instead of local modules) | ✅ |
| 2  | Missing version constraints for third-party dependencies                 | ✅ |
| 3  | `OAuth2AuthorizationServerConfigurer` package guesswork                  | ✅ |
| 4  | `ProviderSettings`/`TokenSettings` moved packages                        | ✅ |
| 5  | `InMemoryUserDetailsManager` moved packages                              | ✅ |
| 6  | Invalid Gradle project paths (`:core` instead of `:spring-security-core`) | ✅ |
| 7  | Code formatting check failure                                            | ✅ |
| 8  | JDK bytecode version mismatch (Java 25 → Java 17)                        | ✅ |
| 9  | Boot plugin version mismatch with the repo's actual SNAPSHOT             | ⚠️ Partial¹ |
| 10 | Relocated SQL schema resource (`oauth2-registered-client-schema.sql`)    | ✅ |
| 11 | Missing `OAuth2AuthorizedClientRepository` bean                          | ✅ |

> ¹ `bootJar` still fails due to a `NoSuchFieldError: TOOLS` in the Boot 4.1.0-SNAPSHOT plugin itself — likely an upstream bug. **Workaround:** use `bootRun` instead.

---

## Changes Made

### 1. `oauth2/oauth2-demo/build.gradle` — Full Rewrite

| Aspect               | Before                          | After                                           |
|----------------------|---------------------------------|-------------------------------------------------|
| **Boot plugin**      | `id 'org.springframework.boot' version '3.1.3'` | `id 'org.springframework.boot' version "${springBootVersion}"` (→ `4.1.0-SNAPSHOT`) |
| **Dep. mgmt plugin** | `id 'io.spring.dependency-management' version '1.1.0'` | `id 'io.spring.dependency-management' version '1.1.7'` |
| **Java target**      | default (JVM version)           | `sourceCompatibility = VERSION_17`, `targetCompatibility = VERSION_17` |
| **repositories {}**  | `mavenCentral()`                | Removed entirely²                               |

² The project's own `repositories {}` block was silently overriding the centrally-managed repositories (`mavenCentral()` + `repo.spring.io/snapshot`) declared in `settings.gradle`'s `dependencyResolutionManagement`. Since this repo builds against `springBootVersion=4.1.0-SNAPSHOT`, the snapshot repo is required. Removing the local block lets the central config apply.

**Dependencies — before (Maven coordinates):**
```groovy
dependencies {
    implementation 'org.springframework.security:spring-security-oauth2-authorization-server:1.3.0'
    implementation 'org.springframework.security:spring-security-oauth2-client:6.3.1'
    implementation 'org.springframework.security:spring-security-oauth2-resource-server:6.3.1'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    // no JPA, no H2
}
```

**Dependencies — after (local project references):**
```groovy
dependencies {
    implementation platform(project(':spring-security-dependencies'))   // pins third-party versions

    implementation project(':spring-security-core')
    implementation project(':spring-security-config')
    implementation project(':spring-security-web')
    implementation project(':spring-security-oauth2-authorization-server')
    implementation project(':spring-security-oauth2-client')
    implementation project(':spring-security-oauth2-resource-server')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'    // added
    implementation 'com.h2database:h2'                                        // added

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Key changes:
- **Removed** `spring-boot-starter-security` (it transitively pulls published jars that conflict with local `project(...)` references)
- **Removed** all published OAuth2 artifact coordinates — the local source modules are the source of truth
- **Added** `platform(project(':spring-security-dependencies'))` to resolve third-party versions (Nimbus JOSE, Jackson, etc.)
- **Added** `spring-boot-starter-data-jpa` and `com.h2database:h2` for the embedded H2 database
- **Kept** comments noting the `spring-security-oauth2-jose` / `spring-security-oauth2-core` modules are available if needed

### 2. Root `settings.gradle` — Plugin Management

Added a `pluginManagement` block so SNAPSHOT Boot plugin versions can be resolved:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = "https://repo.spring.io/snapshot" }
    }
}
```

### 3. All Java Source Files — Updated Imports & APIs

The Java code was rewritten from scratch (original used a different package and older API surface).

**Source layout (7 files under `sample.oauth2.demo`):**

```
src/main/java/sample/oauth2/demo/
├── OAuth2DemoApplication.java          — @SpringBootApplication entry point
├── client/
│   └── OAuth2FlowExample.java          — OAuth2 client token-obtention helper
├── config/
│   ├── AuthorizationServerConfig.java  — H2 DB, JdbcRegisteredClientRepository, JWKSource, AS filter chain
│   ├── ResourceServerConfig.java       — Placeholder (JwtDecoder in SecurityConfig)
│   └── SecurityConfig.java             — Filter chains, in-memory users, ClientRegistration, JwtDecoder
└── web/
    ├── DatabaseViewerController.java   — REST endpoints: /db/clients, /db/authorizations, /db/consents
    └── DemoController.java             — REST endpoints: /, /user, /admin
```

#### Key API Migrations in Source Code

| Old API                                    | New API                                                           |
|--------------------------------------------|-------------------------------------------------------------------|
| `ProviderSettings` (removed package)       | `AuthorizationServerSettings` (`…settings.AuthorizationServerSettings`) |
| `TokenSettings` (old package)              | `TokenSettings` (`…settings.TokenSettings`)                       |
| `InMemoryUserDetailsManager` (`…userdetails`) | `InMemoryUserDetailsManager` (`…provisioning`)                 |
| `OAuth2AuthorizationServerConfigurer` (guessed path) | `…configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer` ✅ |
| `http.apply(configurer)`                   | `http.with(configurer, Customizer.withDefaults())`               |
| `oauth2-registered-client-schema.sql` (root) | `…authorization/client/oauth2-registered-client-schema.sql`    |

#### Corrected Import (Issue 3)

The `OAuth2AuthorizationServerConfigurer` class lives in the **`config`** module, not `oauth2-authorization-server`:

```java
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
```

On this branch it has **no static factory method**, so it's constructed directly:

```java
OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
    .with(authorizationServerConfigurer, Customizer.withDefaults())
    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
    .formLogin(Customizer.withDefaults());
return http.build();
```

### 4. `src/main/resources/application.properties`

Standard Spring Boot configuration with:
- Server port `8085`
- H2 in-memory console enabled at `/h2-console`
- Debug-level logging for `org.springframework.security`

---

## Remaining Issues

### ❌ `bootJar` fails (Issue 9)

Even with the correct Boot `4.1.0-SNAPSHOT` plugin version:

```
Execution failed for task ':oauth2-demo:bootJar'.
> Class org.springframework.boot.loader.tools.JarModeLibrary does not have member field 'TOOLS'
```

Root cause analysis points to a **genuine upstream bug** in this specific `4.1.0-SNAPSHOT` Boot plugin build — the `NoSuchFieldError` is thrown from inside `BootZipCopyAction$Processor.writeJarToolsIfNecessary`, not from anything in the demo's configuration.

**Workaround:** Use `bootRun` instead of building a JAR:

```bash
./gradlew :oauth2-demo:bootRun
```

---

## How to Build & Run

```bash
# Fix formatting before any build
./gradlew :oauth2-demo:format

# Build (compiles but bootJar will fail — see above)
./gradlew :oauth2-demo:build

# Run the app (preferred)
./gradlew :oauth2-demo:bootRun
```

---

## Version Summary

| Component                          | Version                    |
|------------------------------------|----------------------------|
| Gradle                             | 9.6.1                      |
| Spring Boot                        | `4.1.0-SNAPSHOT` (via `gradle.properties`) |
| Spring Security                    | `7.1.1-SNAPSHOT` (local source modules)   |
| Spring Dependency Management Plugin | 1.1.7                     |
| Java target                        | 17                         |

---

## Key Lessons

1. **Verify project paths with `gradlew projects`** before writing `project(':…')` dependencies — don't infer them from folder names.
2. **Verify class shapes by reading source on disk** — training-data knowledge reflects *released* APIs, not in-progress SNAPSHOT ones.
3. **Check `gradle.properties` early** — the framework version this repo targets explains almost every API-shape surprise.
4. A module's own `repositories {}` block **overrides** centrally-declared ones in `settings.gradle`; don't add one unless you deliberately want to drop central repos.

