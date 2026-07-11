# OAuth2 Demo Build Error - Root Cause & Fix

## Problem
The `oauth2/oauth2-demo` project fails to build with Gradle 9.6.1 due to API incompatibility issues.

### Error 1: Gradle API Incompatibility (FIXED)
```
NoSuchMethodError: 'java.util.Set org.gradle.api.artifacts.LenientConfiguration.getArtifacts(org.gradle.api.specs.Spec)'
```

**Root Cause:** The project was using outdated Spring Boot and Dependency Management plugin versions:
- `org.springframework.boot:3.1.3` (Sept 2023)
- `io.spring.dependency-management:1.1.0`

These plugins were compiled against older Gradle API versions that had the `getArtifacts(Spec)` method on `LenientConfiguration`. Gradle 9.6.1 (June 2026) removed this method.

**Fix Applied:**
```gradle
// Updated from 3.1.3 to 3.4.1
plugins {
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'java'
}
```

### Error 2: API Changes in Spring Security Authorization Server (PARTIAL)
After fixing Error 1, compilation errors appear:
```
error: package org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization does not exist
error: package org.springframework.security.oauth2.server.authorization.config does not exist
```

**Root Cause:** The oauth2-demo code was written for an older Spring Security authorization server API. The package structure and configuration APIs have changed between versions.

**Status:** The demo code itself needs updates to match the current Spring Security authorization server API. This is beyond the Gradle plugin compatibility issue.

## Note on oauth2-demo
This is a **standalone demo application**, not part of the core Spring Security build. It:
- Uses `build.gradle` (not `spring-security-*.gradle`)
- Has its own repository configuration
- Is meant to be run with published Maven artifacts, not local source modules
- Is excluded from root project checks (format, checkstyle) because project name contains 'demo'

## Versions Used
- **Gradle:** 9.6.1
- **Spring Boot:** 3.4.1 (updated from 3.1.3)
- **Spring Dependency Management Plugin:** 1.1.6 (updated from 1.1.0)
- **Spring Security OAuth2 Authorization Server:** 1.3.0
- **Spring Security OAuth2 Client/Resource Server:** 6.3.1
- **Java:** 17+

## What Still Needs Work
The demo application code itself needs to be updated to use the current Spring Security authorization server APIs. The Gradle build infrastructure is now compatible with Gradle 9.6.1.

