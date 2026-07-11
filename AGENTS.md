# AGENTS.md - Spring Security Development Guide

## Project Overview

Spring Security is a comprehensive authentication and authorization framework for Spring applications. This is a **modular Gradle-based project** with ~20 independent modules organized by feature area. Critical to understand: **module interdependencies are defined in Gradle files**, not Maven POMs, and each module's `spring-security-*.gradle` file specifies its own dependency graph.

## Architecture and Module Organization

### Core Module Hierarchy
- **`spring-security-core`** (root dependency): Authentication, access control, provisioning APIs. Contains `SecurityContextHolder` (thread-local security context storage) and core authorization interfaces.
- **`spring-security-web`** (depends on core): Servlet security filters, request matching, session management. Includes JavaScript resources synced from `spring-security-javascript` module.
- **`spring-security-config`** (depends on web): XML schema-based configuration (`RNC → XSD` conversion) and `@Configuration` support.

### Specialized Modules (Feature-Specific)
- **`oauth2/*`** (4 sub-modules): Authorization server, resource server, client, JOSE. Each is independently testable.
- **`saml2/*`, `cas/`, `ldap/`, `kerberos/*`**: Protocol/integration-specific implementations. Follow same structural pattern.
- **`messaging/`, `rsocket/`, `webauthn/`**: Additional technology integrations.

### Build Organization
- **`buildSrc/`**: Custom Gradle plugins (conventions, versioning, checks)
- **`gradle/libs.versions.toml`**: Centralized dependency management (Gradle 7.4+ format)
- **`itest/`**: Integration tests (organized by feature: web, ldap, oauth2, misc)

### Cross-Cutting Concerns
- **`spring-security-test`**: Testing utilities (MockSecurityContextHolderStrategy, test annotations)
- **`spring-security-crypto`**: Password encoding, encryption (dependency of core)
- **`docs/`**: Antora-based documentation with module-specific pages

## Critical Developer Workflows

### Build & Test Lifecycle
```bash
# Format code (must run before commit)
./gradlew format

# Run checks (includes formatting, checkstyle, nullability, dependency checks)
./gradlew check

# Full build with tests
./gradlew build

# Integration tests (slower, separate from unit tests)
./gradlew integrationTest

# Specific module build
./gradlew :spring-security-core:build

# Build reference docs (Antora)
./gradlew :spring-security-docs:antora
```

**Critical**: Format + check are **mandatory before committing**. Build errors often stem from format violations, not logic errors. The `check` task performs multiple validations including NoHttp (no http:// URLs in source).

### Code Style Essentials
- **Formatting**: Enforced via io.spring.javaformat plugin (55-char subject lines in commits, 72-char line wrapping)
- **Import Order**: Check manually (not enforced by format task) - follow Spring Framework conventions
- **Streams vs. Loops**: `for` loops preferred over streams on hot paths (documented in CONTRIBUTING.adoc)
- **Nullability**: All public APIs must have `@Nullable` or `@NonNull` annotations; plugin enforces this

### Contributing Workflow
1. All commits require **DCO sign-off** (`-s` flag): `git commit -s "message"`
2. Create issue first for features (recommended), direct PR for typos/straightforward fixes
3. Branch against the **milestone branch** (e.g., `5.8.x` for `5.8.3`), not `main` for backports
4. Commands before push: `./gradlew format && ./gradlew check`
5. Update copyright year in edited files to current year

## Architecture Patterns & Integration Points

### SecurityContextHolder Pattern (Thread-Local Storage)
```java
// Core abstraction across ALL modules
SecurityContext ctx = SecurityContextHolder.getContext();
Authentication auth = ctx.getAuthentication();
```
Any modification to authentication state must go through `SecurityContextHolder` strategy (see `MockSecurityContextHolderStrategy` for testing). Reactive modules have `ReactiveSecurityContextHolder` equivalent.

### FilterChainProxy Architecture (Servlet)
- Multiple `SecurityFilterChain` instances per application (one per URL pattern)
- Each chain is independent with unique filter configuration
- `FilterChainProxy` delegates based on URL patterns
- Pattern matching logic in spring-security-web; declarative configuration in spring-security-config

### Module Extension Pattern
Each protocol/technology module (OAuth2, SAML2, LDAP, etc.) follows:
1. Core domain classes in dedicated package
2. Configuration auto-configuration classes ending in `*AutoConfiguration`
3. Jackson2 modules for serialization support (registered via `SecurityJackson2Modules` discovery)

### Dependency Declaration Model
Each module gradle file specifies:
- **`api`**: Public dependencies exported to downstream modules
- **`optional`**: Features that may not be used; can be excluded
- **`testImplementation`**: Test-only dependencies (AssertJ, Mockito, Spring Test)

Example: core depends on `spring-aop` (api) for method security, `jackson-databind` (optional) for JSON support.

## Integration with External Systems

### Spring Framework Integration
All modules build on Spring Framework 7.0+ (or Spring Boot 4.1+ compatible). Key integrations:
- Beans/Context: DI container and lifecycle
- AOP: Method-level security interception
- Expression: SpEL for authorization rules (`@PostAuthorize`)
- Web: DispatcherServlet integration

### OAuth2 & JWT Ecosystem
- **JOSE module**: JWS/JWE encoding (depends on Nimbus JOSE+JWT library)
- **Resource Server**: Validates Bearer tokens (JWT or introspection)
- **Authorization Server**: Issues tokens (OpenID Connect compliant)
- **Client**: Handles token exchange, refresh flows

### External Protocol Dependencies
- **LDAP**: UnboundID SDK (spring-security-ldap)
- **SAML2**: OpenSAML5 library (spring-security-saml2)
- **Kerberos**: JAAS (Java Authentication)

## Project Configuration Quirks & Conventions

### Gradle Peculiarities
- **Dynamic module loading**: `settings.gradle` auto-discovers all `*/build.gradle` files and registers projects
- **Circular dependency workaround**: Custom Eclipse plugin in build to handle Gradle cycles (see root build.gradle)
- **Parallel & caching enabled**: `org.gradle.parallel=true`, `org.gradle.caching=true` in gradle.properties
- **Heap configuration**: Set to 3GB by default; increase if running full build on limited memory

### Module Naming Convention
Module directories must match their Gradle project name. Format: `:spring-security-modulename` → `modulename/` directory

**Important caveat for samples/demos**: Sample projects (oauth2-demo) use `build.gradle` instead of the `spring-security-*.gradle` pattern, resulting in project names like `:oauth2-demo` instead of `:spring-security-oauth2-demo`. These can be built with:
```bash
./gradlew :oauth2-demo:build
```
But they're excluded from certain CI checks (format, checkstyle, checkFormat) when the project name contains 'sample' (see root build.gradle line 65).

### Special Tasks
- **`cloneRepository`**: Custom task for cloning external repositories (used for samples)
- **`verifyDependenciesVersions`**: Checks all deps use versions from central `libs.versions.toml`
- **Develocity (Gradle Build Scans)**: Build scans enabled by default; consents to terms in build.gradle

## Common Pitfalls for AI Agents

1. **Forgetting format before changes**: Code won't pass CI. Always: `./gradlew format && ./gradlew check`
2. **Assuming Maven structure**: This is Gradle. All dependency management is in `libs.versions.toml`, not POM files
3. **Changing XSD directly**: Update RNC files instead, then run: `./gradlew :spring-security-config:rncToXsd`
4. **Missing nullability annotations**: Public APIs require explicit `@Nullable` on parameters/returns
5. **Using streams on hot paths**: Hot path code must use `for` loops; document why if using streams
6. **Forgetting copyright year updates**: Update in any edited file's header
7. **Direct module dependency edits**: Edit `spring-security-modulename.gradle`, not root build.gradle
8. **Ignoring integration tests**: Unit tests (`:test`) are fast; `:integrationTest` is separate and slower
9. **oauth2-demo build failures**: This demo uses outdated Spring Boot (3.1.3) and is excluded from the root multi-module build. If updating plugin versions or Spring Security versions, note that `oauth2/oauth2-demo/build.gradle` requires separate version updates. See `oauth2/oauth2-demo/GRADLE_FIX_SUMMARY.md` for known issues and fixes.

## Key Files to Review

- **`gradle/libs.versions.toml`**: Centralized versions for all dependencies
- **`buildSrc/src/main/groovy/`**: Convention plugins (compilation, checking, formatting)
- **`docs/modules/ROOT/pages/servlet/architecture.adoc`**: Detailed security filter chain documentation
- **`CONTRIBUTING.adoc`**: Comprehensive contribution requirements
- **`*/src/main/java/org/springframework/security/*/`**: Each module's core packages
- **`itest/*/src/test/java/`**: Integration test examples by feature

---

*Last updated: July 2026. For architecture deep-dives, consult docs/modules/ROOT/pages/servlet/architecture.adoc and test source in itest/ directories.*
