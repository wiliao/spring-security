# oauth2-demo Build Troubleshooting

This document records the issues encountered getting `oauth2-demo` to build and run inside
the `spring-security-fork` monorepo, why each one happened, and how it was fixed. The repo
builds against **Spring Boot `4.1.0-SNAPSHOT`** / **Spring Security `7.1.1-SNAPSHOT`**
(see `gradle.properties`), which is the root cause behind most of these issues: the sample
module was originally written against a much older, released API shape.

## Context: what kind of repo this is

`oauth2-demo` is **not** a standalone Spring Boot app pulling published Maven Central
artifacts. It's a sample module living *inside* the actual `spring-projects/spring-security`
build (this fork), sitting alongside the framework's own modules (`core`, `config`, `web`,
`oauth2/oauth2-*`, etc.). That distinction drives almost every fix below — dependencies need
to point at local, in-repo source rather than released artifact coordinates, because the
whole point is to build against whatever state those modules are actually in on this branch.

Confirm this yourself with:

```powershell
.\gradlew.bat projects
```

which prints every real module and its Gradle project path.

---

## Issue 1 — Wrong dependency coordinates (Maven Central versions instead of local modules)

**Symptom**

```
error: package org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization does not exist
error: package org.springframework.security.oauth2.server.authorization.config does not exist
error: cannot find symbol ... InMemoryUserDetailsManager
```

**Cause**

`build.gradle` declared published Maven coordinates and versions
(`org.springframework.security:spring-security-oauth2-authorization-server:7.0.5`, etc.)
that don't correspond to any released, coherent version — and even if they did, this repo's
own local module source is the actual source of truth here, not Maven Central.

**Fix**

Depend on the local sibling modules by their real Gradle project paths (verified via
`gradlew projects` — this build uses **flat, prefixed** paths, not nested ones):

```groovy
dependencies {
    implementation project(':spring-security-core')
    implementation project(':spring-security-config')
    implementation project(':spring-security-web')
    implementation project(':spring-security-oauth2-authorization-server')
    implementation project(':spring-security-oauth2-client')
    implementation project(':spring-security-oauth2-resource-server')
}
```

Removed `spring-boot-starter-security` entirely — it transitively pulls the *published*
`spring-security-config`/`spring-security-web` jars, which would conflict with the local
`project(...)` versions above.

---

## Issue 2 — Missing version constraints for third-party dependencies

**Symptom**

```
Could not find com.nimbusds:nimbus-jose-jwt:.
Could not find tools.jackson.core:jackson-databind:.
Could not find com.nimbusds:oauth2-oidc-sdk:.
```

Note the **trailing colon with no version** — these aren't missing from Maven Central,
they're declared *without* a version by the local modules.

**Cause**

This repo centralizes third-party dependency versions via a Gradle **platform** project,
`:spring-security-dependencies`. It's applied automatically to the framework's own
subprojects but not to `oauth2-demo`, since it sits outside that convention.

**Fix**

```groovy
dependencies {
    implementation platform(project(':spring-security-dependencies'))
    // ...
}
```

---

## Issue 3 — `OAuth2AuthorizationServerConfigurer` package guesswork

**Symptom (after an incorrect "fix")**

```
error: package org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers does not exist
```

**Cause**

The original import was actually **correct**:

```java
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
```

This class implements the `HttpSecurity` DSL, so on this branch it lives in the **`config`**
module (alongside every other `http.with(...)` configurer) — not in `oauth2-authorization-server`,
which only holds protocol/domain logic (filters, providers, tokens). An earlier guess "fixed"
this to a plausible-looking but nonexistent published-API path. Don't trust package-path
guesses on a SNAPSHOT branch — verify on disk:

```powershell
Get-ChildItem -Recurse -Filter "OAuth2AuthorizationServerConfigurer.java" -Path .\config
```

**Fix**

Reverted to the original import. Confirmed the class shape directly before writing code
against it:

```powershell
Select-String -Path <file> -Pattern "public class|public static|extends AbstractHttpConfigurer|getEndpointsMatcher"
```

which showed:

```java
public final class OAuth2AuthorizationServerConfigurer
        extends AbstractHttpConfigurer<OAuth2AuthorizationServerConfigurer, HttpSecurity> {
    public RequestMatcher getEndpointsMatcher() { ... }
}
```

No static factory method exists on this branch, so it's constructed directly and applied via
`http.with(...)` (the non-deprecated replacement for `http.apply(...)`):

```java
OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
        .with(authorizationServerConfigurer, Customizer.withDefaults())
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults());

return http.build();
```

---

## Issue 4 — `ProviderSettings`/`TokenSettings` moved packages

**Symptom**

```
error: package org.springframework.security.oauth2.server.authorization.config does not exist
```

**Fix**

`ProviderSettings` was renamed to `AuthorizationServerSettings`, and both it and
`TokenSettings` moved to the `settings` subpackage:

```java
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
```

```java
@Bean
public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().issuer("http://localhost:8085").build();
}
```

---

## Issue 5 — `InMemoryUserDetailsManager` moved packages

**Symptom**

```
error: cannot find symbol
import org.springframework.security.core.userdetails.InMemoryUserDetailsManager;
```

**Fix**

Confirmed via file search that it now lives under `org.springframework.security.provisioning`:

```powershell
Get-ChildItem -Recurse -Filter "InMemoryUserDetailsManager.java" -Path .
# -> core\src\main\java\org\springframework\security\provisioning\InMemoryUserDetailsManager.java
```

```java
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
```

---

## Issue 6 — Invalid Gradle project paths (`':core'`, `':oauth2:oauth2-*'`)

**Symptom**

```
Project with path ':core' could not be found.
```

**Cause**

Guessed project paths from folder names (`core/`, `oauth2/oauth2-authorization-server/`)
rather than the paths Gradle actually resolves. This repo's `settings.gradle` computes
project paths programmatically (scans for `build.gradle` files and derives names from
directories), which doesn't map 1:1 to folder paths.

**Fix**

Get the ground truth instead of guessing:

```powershell
.\gradlew.bat projects
```

Real paths are flat and prefixed, e.g. `:spring-security-core`, `:spring-security-config`,
`:spring-security-oauth2-authorization-server` — not `:core` or `:oauth2:oauth2-authorization-server`.

---

## Issue 7 — Code formatting check failure

**Symptom**

```
Execution failed for task ':oauth2-demo:checkFormatMain'.
> Formatting violations found in the following files: ...
```

**Cause**

This repo enforces a strict code style (Spring Java Format-style: import ordering, spacing,
brace placement) across every module, including `oauth2-demo`.

**Fix**

```powershell
.\gradlew.bat :oauth2-demo:format
```

Re-run this any time a touched file trips the formatter again.

---

## Issue 8 — JDK bytecode version mismatch

**Symptom**

```
Execution failed for task ':oauth2-demo:resolveMainClassName'.
> Unsupported class file major version 69
```

**Cause**

Class file major version 69 = Java 25. The default/newest JDK on the machine compiled
`oauth2-demo` to Java 25 bytecode, but the ASM library bundled in this version of the Spring
Boot Gradle plugin's tooling can't parse class files that new.

**Fix**

Pin the compiled bytecode target explicitly (Java 21, an LTS release well within Spring
Boot's supported range), without needing to switch the system JDK:

```groovy
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

(Java 17 was used in the end, but 21 works equally well — pick whichever LTS your Boot
version supports and your compiler can cross-target via `--release`.)

---

## Issue 9 — Boot plugin version mismatch with the repo's actual SNAPSHOT

**Symptom**

```
Execution failed for task ':oauth2-demo:bootJar'.
> Class org.springframework.boot.loader.tools.JarModeLibrary does not have member field
  'org.springframework.boot.loader.tools.JarModeLibrary TOOLS'
```

**Cause**

`build.gradle` hardcoded `id 'org.springframework.boot' version '3.5.16'`, but the repo
actually builds against `springBootVersion=4.1.0-SNAPSHOT` (see `gradle.properties`). The
mismatch between the plugin version and the `spring-boot-loader-tools` jar the local modules
are actually built against causes this field-not-found error at `bootJar` time.

**Fix**

Track the real version from `gradle.properties`:

```groovy
plugins {
    id 'org.springframework.boot' version "${springBootVersion}"
}
```

This also required adding the Spring snapshot repository to **plugin resolution**
specifically (SNAPSHOT plugin builds aren't hosted on Gradle Plugin Portal), in the root
`settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = "https://repo.spring.io/snapshot" }
    }
}
```

...and removing `oauth2-demo/build.gradle`'s own `repositories { mavenCentral() }` block,
which was silently overriding the centrally-managed repositories declared in
`settings.gradle`'s `dependencyResolutionManagement` (which already included the snapshot
repo needed to resolve `4.1.0-SNAPSHOT` dependency artifacts).

**Status:** even with the correct plugin version and a `--refresh-dependencies` run, `bootJar`
still fails with the same error. Deeper investigation ruled out every locally-fixable cause:

- Confirmed via `javap` on the actual resolved `4.1.0-SNAPSHOT` `loader-tools` jar that the
  `TOOLS` field genuinely exists in the class Gradle resolved.
- Ruled out Gradle Daemon classloader staleness (`--stop` + `--no-daemon` still fails
  identically).
- Ruled out `buildSrc` leaking an older `spring-boot-gradle-plugin`/`loader-tools` dependency
  into the build classpath (no Boot references anywhere in `buildSrc`).
- Ruled out the root `build.gradle` forcing a different Boot version via a resolution
  strategy (none present).

The stack trace shows the `NoSuchFieldError` is thrown from inside the Boot Gradle plugin's
own compiled code (`BootZipCopyAction$Processor.writeJarToolsIfNecessary`), not from anything
in `oauth2-demo`'s configuration. This looks like a genuine internal inconsistency in this
specific Boot 4.1.0-SNAPSHOT plugin build — worth reporting upstream or waiting for a newer
SNAPSHOT, not something fixable from this module's `build.gradle`. **Workaround:** use
`bootRun` instead of `bootJar`/building a jar; it never touches the layered-jar tooling that's
broken.

```powershell
.\gradlew.bat :oauth2-demo:bootRun
```

---

## Issue 10 — Relocated SQL schema resource

**Symptom**

```
Caused by: java.io.FileNotFoundException: class path resource
[org/springframework/security/oauth2/server/authorization/oauth2-registered-client-schema.sql]
cannot be opened because it does not exist
```

**Cause**

Of the three Authorization Server schema files, only `oauth2-registered-client-schema.sql`
moved — into a `client` subpackage, alongside `JdbcRegisteredClientRepository`:

```powershell
Get-ChildItem -Recurse -Filter "*.sql" -Path .\oauth2\oauth2-authorization-server\src\main\resources
```

```
...\authorization\oauth2-authorization-schema.sql            (unchanged)
...\authorization\oauth2-authorization-consent-schema.sql     (unchanged)
...\authorization\client\oauth2-registered-client-schema.sql  (moved)
```

**Fix**

```java
.addScript("org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql")
.addScript("org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql")
.addScript("org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql")
```

---

## Issue 11 — Missing `OAuth2AuthorizedClientRepository` bean

**Symptom**

```
Field oAuth2AuthorizedClientRepository in sample.oauth2.demo.client.OAuth2FlowExample
required a bean of type 'org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository'
that could not be found.
```

**Cause**

`OAuth2FlowExample` autowires this type directly. Spring Boot normally autoconfigures it once
a `ClientRegistrationRepository` bean exists (which `SecurityConfig` does provide), but that
autoconfiguration wasn't firing on this SNAPSHOT.

**Fix**

Declare it explicitly rather than depend on autoconfiguration behavior:

```java
@Bean
public OAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
}
```

---

## Final working configuration

### `oauth2-demo/build.gradle`

```groovy
plugins {
    id 'org.springframework.boot' version "${springBootVersion}"
    id 'io.spring.dependency-management' version '1.1.7'
    id 'java'
}

group = 'org.springframework.security.samples'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// No repositories {} block here deliberately - it would override the centrally-managed
// repositories (mavenCentral() + repo.spring.io/snapshot) declared in the root settings.gradle.

dependencies {
    implementation platform(project(':spring-security-dependencies'))

    implementation project(':spring-security-core')
    implementation project(':spring-security-config')
    implementation project(':spring-security-web')
    implementation project(':spring-security-oauth2-authorization-server')
    implementation project(':spring-security-oauth2-client')
    implementation project(':spring-security-oauth2-resource-server')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'com.h2database:h2'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### Root `settings.gradle` addition

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = "https://repo.spring.io/snapshot" }
    }
}
```

### Standard build/run commands

```powershell
.\gradlew.bat :oauth2-demo:format   # fix formatting before every build
.\gradlew.bat :oauth2-demo:build    # bootJar currently broken - see Issue 9
.\gradlew.bat :oauth2-demo:bootRun  # actually run the app
```

---

## Lessons for next time

1. **Verify project paths with `gradlew projects`** before writing any `project(':...')`
   dependency — don't infer them from folder names.
2. **Verify package paths/class shapes by reading the actual source on disk**
   (`Get-ChildItem`, `Select-String`) before writing imports or API calls against a SNAPSHOT
   branch — training-data knowledge of "current" Spring Security APIs reflects released
   versions, not in-progress ones.
3. **Check `gradle.properties` early** for the actual framework version this repo targets —
   it explains almost every API-shape surprise encountered here.
4. A project's own `repositories {}` block **overrides**, rather than adds to, centrally
   declared repositories in `settings.gradle`'s `dependencyResolutionManagement`. Don't
   declare one unless you deliberately want to drop the central ones.
