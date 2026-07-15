# AGENTS.md - Spring Security Development Guide

## Project Overview

Spring Security is a comprehensive authentication and authorization framework for Spring applications. This is a **modular Gradle-based project** with ~20 independent modules organized by feature area. Critical to understand: **module interdependencies are defined in Gradle files**, not Maven POMs, and each module's `spring-security-*.gradle` file specifies its own dependency graph.

## Architecture and Module Organization

### Core Module Hierarchy
- **`spring-security-core`** (root dependency): Authentication, access control, provisioning APIs. Contains `SecurityContextHolder` (thread-local security context storage) and core authorization interfaces.
- **`spring-security-web`** (depends on core): Servlet security filters, request matching, session management. Includes JavaScript resources synced from `spring-security-javascript` module.
- **`spring-security-config`** (depends on web): XML schema-based configuration (`RNC → XSD` conversion) and `@Configuration` support.
- **`spring-security-access`** (legacy, depends on core): **Spring Security 7 migration module** containing the old `AccessDecisionManager`, `AccessDecisionVoter`, `ConfigAttribute`, `AfterInvocationProvider` and related Access API previously in core. Located at `access/`. See migration docs in `docs/modules/ROOT/pages/migration/servlet/authorization.adoc`.
- **`spring-security-data`** (depends on core): Spring Data integration with `SecurityEvaluationContextExtension`. Located at `data/`.
- **`dependencies/`** (`spring-security-dependencies`): **Build infrastructure** — a `java-platform` project that centrally manages all dependency BOMs (Spring Framework, Reactor, Jackson 2/3, JUnit, etc.) and ~50 individual library constraints. Located at `dependencies/`.
- **`bom/`** (`spring-security-bom`): **Build infrastructure** — aggregates all `SpringModulePlugin` projects into a BOM for downstream consumers. Located at `bom/`.

### Specialized Modules (Feature-Specific)
- **`oauth2/*`** (5 sub-modules): Authorization server, resource server, client, JOSE, core. Each is independently testable.
- **`saml2/*`, `cas/`, `ldap/`, `kerberos/*`**: Protocol/integration-specific implementations. Follow same structural pattern.
- **`messaging/`, `rsocket/`, `webauthn/`**: Additional technology integrations.

### Build Organization
- **`buildSrc/`**: Custom Gradle plugins (conventions, versioning, checks). Includes `compile-warnings-error`, `javadoc-warnings-error`, `security-nullability`, `test-compile-target-jdk25`, `java-toolchain`, and `security-kotlin` convention plugins. Also registers 6 additional plugins: `trang` (RNC→XSD), `locks` (GlobalLockPlugin), `io.spring.convention.management-configuration`, `s101`, `verify-dependencies-versions`, and `check-expected-branch-version`.
- **`dependencies/`** (`spring-security-dependencies`): java-platform aggregator for all BOMs (Spring, Reactor, Jackson 2/3, JUnit, Mockito, Kotlin) and library constraints. Located at `dependencies/spring-security-dependencies.gradle`.
- **`bom/`** (`spring-security-bom`): Bill of Materials aggregating all `SpringModulePlugin` projects for downstream consumers. Located at `bom/spring-security-bom.gradle`.
- **`gradle/libs.versions.toml`**: Centralized dependency management (Gradle 7.4+ format)
- **`itest/`**: Integration tests (organized by feature: context, ldap, web, misc)

### Cross-Cutting Concerns
- **`spring-security-test`**: Testing utilities (MockSecurityContextHolderStrategy, test annotations)
- **`spring-security-crypto`**: Password encoding, encryption (dependency of core). Uses `com.password4j:password4j` as optional dependency.
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
- **Nullability**: All public APIs must have `@Nullable` or `@NonNull` annotations; `security-nullability` plugin (`io.spring.nullability`) enforces this
- **Java Toolchain**: Default toolchain is JDK 25; production code compiles to `--release 17`, test code to `--release 25` (due to Security Manager removal in JDK 25). Configured in `buildSrc/src/main/groovy/java-toolchain.gradle` and `test-compile-target-jdk25.gradle`.
- **Compile Warnings**: Many modules apply `compile-warnings-error` plugin (`-Werror` for Java, `allWarningsAsErrors` for Kotlin) and `javadoc-warnings-error` plugin (`-Werror` for Javadoc).

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
- **WebAuthn**: `webauthn4j-core` library (spring-security-webauthn)

### Jackson 2/3 Dual Support
The project is migrating from Jackson 2 (`com.fasterxml.jackson`) to Jackson 3 (`tools.jackson`). Both BOMs are managed in `spring-security-dependencies`. Modules may declare optional dependencies on both coordinates. See `gradle/libs.versions.toml` for version definitions.

## Project Configuration Quirks & Conventions

### Gradle Peculiarities
- **Dynamic module loading**: `settings.gradle` auto-discovers all `*/build.gradle` and `*.gradle.kts` files and registers projects. Excludes `build`, `**/gradle`, `settings.gradle`, `buildSrc`, `/build.gradle`, `.*`, `out`, and `**/grails3`. Also supports an `excludeProjects` project property override.
- **Circular dependency workaround**: Custom Eclipse plugin in build to handle Gradle cycles (see root build.gradle)
- **Parallel & caching enabled**: `org.gradle.parallel=true`, `org.gradle.caching=true` in gradle.properties
- **Heap configuration**: Set to 3GB by default (`-Xmx3g`); increase if running full build on limited memory
- **Kotlin default dependency**: `kotlin.stdlib.default.dependency=false` in gradle.properties; Kotlin modules explicitly declare `kotlin-stdlib-jdk8` dependency
- **Gradle Wrapper Upgrade**: Configured via `org.gradle.wrapper-upgrade` plugin with `baseBranch = '6.3.x'` (updates on 6.3.x, merged forward to main)
- **Project version**: `version=7.1.1-SNAPSHOT`, `springBootVersion=4.1.0-SNAPSHOT` in `gradle.properties`

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
- **`check-expected-branch-version`**: Validates branch naming conventions
- **Develocity (Gradle Build Scans)**: Build scans enabled by default; consents to terms in build.gradle
- **`springRelease`**: Configured in root `build.gradle` with release cadence (3rd week, Mondays), reference doc and API doc URLs
- **`wrapperUpgrade`**: Gradle wrapper auto-upgrade via `org.gradle.wrapper-upgrade` plugin targeting `6.3.x` base branch

## Common Pitfalls for AI Agents

1. **Forgetting format before changes**: Code won't pass CI. Always: `./gradlew format && ./gradlew check`
2. **Assuming Maven structure**: This is Gradle. All dependency management is in `libs.versions.toml`, not POM files
3. **Changing XSD directly**: Update RNC files instead, then run: `./gradlew :spring-security-config:rncToXsd`
4. **Missing nullability annotations**: Public APIs require explicit `@Nullable` or `@NonNull` on parameters/returns
5. **Using streams on hot paths**: Hot path code must use `for` loops; document why if using streams
6. **Forgetting copyright year updates**: Update in any edited file's header
7. **Direct module dependency edits**: Edit `spring-security-modulename.gradle`, not root build.gradle
8. **Ignoring integration tests**: Unit tests (`:test`) are fast; `:integrationTest` is separate and slower
9. **oauth2-demo build failures**: This demo uses outdated Spring Boot (3.1.3) and is excluded from the root multi-module build. If updating plugin versions or Spring Security versions, note that `oauth2/oauth2-demo/build.gradle` requires separate version updates. See `oauth2/oauth2-demo/GRADLE_FIX_SUMMARY.md` for known issues and fixes.
10. **Adding dependencies without checking Jackson 2/3 dual support**: Modules may need optional declarations for both `com.fasterxml.jackson.core:jackson-databind` AND `tools.jackson.core:jackson-databind` during the Jackson migration.
11. **JDK 25 test compilation**: Tests compile with `--release 25` (not 17) because JDK 25 removed the Security Manager. Use `test-compile-target-jdk25` plugin if a module's tests call Security Manager APIs like `Subject.getSubject(AccessControlContext)`.
12. **Compile/Javadoc warnings become errors**: The `compile-warnings-error` and `javadoc-warnings-error` plugins cause warnings to fail builds. Keep code clean of warnings.

## Key Files to Review

- **`gradle/libs.versions.toml`**: Centralized versions for all dependencies
- **`gradle.properties`**: Project version (`7.1.1-SNAPSHOT`), `springBootVersion`, build JVM args, parallelism settings
- **`buildSrc/build.gradle`**: Plugin registrations for `trang`, `locks`, `s101`, `verify-dependencies-versions`, `check-expected-branch-version`
- **`buildSrc/src/main/groovy/`**: Convention plugins (compilation, checking, formatting, nullability, toolchain)
- **`dependencies/spring-security-dependencies.gradle`**: java-platform BOM aggregator (Spring, Reactor, Jackson 2/3, JUnit, Mockito, Kotlin + constraints)
- **`bom/spring-security-bom.gradle`**: BOM aggregating all `SpringModulePlugin` projects for downstream consumers
- **`docs/modules/ROOT/pages/servlet/architecture.adoc`**: Detailed security filter chain documentation
- **`CONTRIBUTING.adoc`**: Comprehensive contribution requirements
- **`*/src/main/java/org/springframework/security/*/`**: Each module's core packages
- **`itest/*/src/test/java/`**: Integration test examples by feature
- **`docs/modules/ROOT/pages/migration/`**: Migration guides for Spring Security 7 and 8 (servlet/, reactive.adoc, index.adoc)

---

*Last updated: July 2026. For architecture deep-dives, consult docs/modules/ROOT/pages/servlet/architecture.adoc and test source in itest/ directories.*
