# OAuth2 Demo Build — Root Cause & Fix Summary

## Problem

The `oauth2/oauth2-demo` module failed to build against the current Spring Security fork (targeting **Spring Boot `4.1.0-SNAPSHOT`** / **Spring Security `7.1.1-SNAPSHOT`**). The demo was originally written against older, released APIs and was never adapted to work inside the monorepo as a local sibling module.

Eleven distinct issues were identified. The detailed troubleshooting log is at `docs/oauth2-demo-build-troubleshooting.md`.

---

## Issues Overview

| #  | Issue                                                                   | Status   |
|----|-------------------------------------------------------------------------|----------|
| 1  | Wrong dependency coordinates (Maven Central versions instead of local modules) | ✅ Fixed |
| 2  | Missing version constraints for third-party dependencies                 | ✅ Fixed |
| 3  | `OAuth2AuthorizationServerConfigurer` package guesswork                  | ✅ Fixed |
| 4  | `ProviderSettings`/`TokenSettings` moved packages                        | ✅ Fixed |
| 5  | `InMemoryUserDetailsManager` moved packages                              | ✅ Fixed |
| 6  | Invalid Gradle project paths (`:core` instead of `:spring-security-core`) | ✅ Fixed |
| 7  | Code formatting check failure                                            | ✅ Fixed |
| 8  | JDK bytecode version mismatch (defaulted to Java 25)                     | ✅ Fixed |
| 9  | Boot plugin version mismatch with the repo's actual SNAPSHOT             | ⚠️ Partial¹ |
| 10 | Relocated SQL schema resource (`oauth2-registered-client-schema.sql`)     | ✅ Fixed |
| 11 | Missing `OAuth2AuthorizedClientRepository` bean                          | ✅ Fixed |

> ¹ `bootJar` still fails due to a `NoSuchFieldError: TOOLS` inside the Boot `4.1.0-SNAPSHOT` plugin itself (upstream bug). **Workaround:** use `bootRun`.

---

## Error 1 — Gradle Plugin API Incompatibility (FIXED)

```
NoSuchMethodError: 'java.util.Set org.gradle.api.artifacts.LenientConfiguration.getArtifacts(org.gradle.api.specs.Spec)'
```

**Root Cause:** Outdated plugin versions compiled against a Gradle API (`LenientConfiguration.getArtifacts(Spec)`) that was removed in Gradle 9.6.1:
- `org.springframework.boot` — originally `3.1.3` (Sept 2023)
- `io.spring.dependency-management` — originally `1.1.0`

**Fix Applied:**
```gradle
plugins {
    id 'org.springframework.boot' version "${springBootVersion}"    // → 4.1.0-SNAPSHOT
    id 'io.spring.dependency-management' version '1.1.7'
    id 'java'
}
```

The Boot plugin version now tracks the canonical version declared in root `gradle.properties` (`springBootVersion=4.1.0-SNAPSHOT`).

Also removed the module's own `repositories {}` block, which was silently overriding the centrally-managed repositories (`mavenCentral()` + `repo.spring.io/snapshot`) declared in `settings.gradle`'s `dependencyResolutionManagement`. Without that fix, the `4.1.0-SNAPSHOT` plugin artifact could not be resolved.

The root `settings.gradle` also gained a `pluginManagement` block to resolve SNAPSHOT plugins:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = "https://repo.spring.io/snapshot" }
    }
}
```

---

## Error 2 — Spring Security API Changes (FIXED)

After fixing Error 1, compilation errors appeared:

```
error: package org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization does not exist
error: package org.springframework.security.oauth2.server.authorization.config does not exist
error: cannot find symbol ... InMemoryUserDetailsManager
```

**Root Cause:** The demo used an older Spring Security API surface. Several classes moved packages or were renamed between the old release the demo was written against and the current `7.1.1-SNAPSHOT` branch.

**Fixes Applied (all 7 Java source files updated):**

| Old API                                           | New API                                                             |
|---------------------------------------------------|---------------------------------------------------------------------|
| `ProviderSettings` (removed package)              | `AuthorizationServerSettings` (`…settings.AuthorizationServerSettings`) |
| `TokenSettings` (old package)                     | `TokenSettings` (`…settings.TokenSettings`)                         |
| `InMemoryUserDetailsManager` (`…userdetails`)     | `InMemoryUserDetailsManager` (`…provisioning`)                      |
| Published Maven dependency coordinates            | Local `project(':spring-security-*')` references                    |
| `http.apply(configurer)`                          | `http.with(configurer, Customizer.withDefaults())`                  |
| `oauth2-registered-client-schema.sql` (root)      | `…authorization/client/oauth2-registered-client-schema.sql`        |

**Note on `OAuth2AuthorizationServerConfigurer`:** The correct import —
```java
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
```
— is valid on this branch. The class lives in the **`config`** module (not `oauth2-authorization-server`) because it implements the `HttpSecurity` DSL. On this branch it has **no static factory method**, so it is constructed directly and applied via `http.with(...)`.

---

## Error 3 — `bootJar` Fails (UNRESOLVED — Upstream Bug)

```
Execution failed for task ':oauth2-demo:bootJar'.
> Class org.springframework.boot.loader.tools.JarModeLibrary does not have member field 'TOOLS'
```

Even with the correct Boot `4.1.0-SNAPSHOT` plugin version, `bootJar` fails. Investigation ruled out:
- ❌ Stale Gradle daemon classloader (`--stop` + `--no-daemon` still fails)
- ❌ `buildSrc` leaking older Boot dependencies (no Boot references in `buildSrc`)
- ❌ Root `build.gradle` resolution strategy forcing a different version

The `NoSuchFieldError` is thrown from inside `BootZipCopyAction$Processor.writeJarToolsIfNecessary` — confirmed that the `TOOLS` field **does** exist in the resolved `4.1.0-SNAPSHOT` `loader-tools` jar via `javap`. This appears to be a **genuine internal inconsistency** in this specific Boot SNAPSHOT build.

**Workaround:** Use `bootRun` instead:
```bash
./gradlew :oauth2-demo:bootRun
```

---

## What Was Actually Changed

| File                                    | Change Summary                                                |
|-----------------------------------------|---------------------------------------------------------------|
| `oauth2/oauth2-demo/build.gradle`       | Full rewrite — local deps, platform, no repositories block, Java 17 target |
| `settings.gradle` (root)                | Added `pluginManagement` block for SNAPSHOT plugin resolution |
| `AuthorizationServerConfig.java`        | Rewritten — H2 DB, `JdbcRegisteredClientRepository`, `JWKSource`, `AuthorizationServerSettings`, corrected `OAuth2AuthorizationServerConfigurer` usage |
| `SecurityConfig.java`                   | Rewritten — filter chains, in-memory users, `ClientRegistration`, `JwtDecoder`, `OAuth2AuthorizedClientRepository` |
| `ResourceServerConfig.java`             | Placeholder created                                             |
| `OAuth2FlowExample.java`                | Helper component for token obtention + protected resource call |
| `DemoController.java`                   | Rest endpoints: `/`, `/user`, `/admin`                         |
| `DatabaseViewerController.java`         | REST endpoints: `/db/clients`, `/db/authorizations`, `/db/consents` |
| `OAuth2DemoApplication.java`            | Standard `@SpringBootApplication` entry point                  |
| `application.properties`                | Port `8085`, H2 console, debug logging                         |
| `oauth2/oauth2-demo/README.md`          | Updated for local build commands                               |
| `oauth2/oauth2-demo/docs/*.md`          | Architecture, troubleshooting, and project structure docs      |

---

## Current Version Summary

| Component                          | Version                               |
|------------------------------------|---------------------------------------|
| Gradle                             | 9.6.1                                 |
| Spring Boot                        | `4.1.0-SNAPSHOT` (via `gradle.properties`) |
| Spring Security                    | `7.1.1-SNAPSHOT` (local source modules)    |
| Spring Dependency Management Plugin | `1.1.7`                               |
| Java target                        | 17                                    |
| Database                           | H2 in-memory (`mem:testdb`)           |

---

## How to Build & Run

```bash
# Fix formatting before any build
./gradlew :oauth2-demo:format

# Build (compiles but bootJar will fail)
./gradlew :oauth2-demo:build

# Run the app
./gradlew :oauth2-demo:bootRun
```

---

## Key Lessons Learned

1. **Verify project paths with `gradlew projects`** before writing `project(':…')` dependencies — don't infer them from folder names.
2. **Verify class shapes by reading source on disk** — training-data knowledge reflects *released* APIs, not in-progress SNAPSHOT ones.
3. **Check `gradle.properties` early** — the framework version this repo targets explains almost every API-shape surprise.
4. A module's own `repositories {}` block **overrides** centrally-declared repositories — don't add one unless you deliberately want to drop the central config.

