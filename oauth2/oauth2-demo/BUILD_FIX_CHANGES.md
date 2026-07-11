# OAuth2 Demo Build Fix - Changes Applied

## Issue Summary
The `oauth2/oauth2-demo/logs/build-error.log` file documented a Gradle dependency resolution failure occurring with Gradle 9.6.1.

**Error:** `NoSuchMethodError: 'java.util.Set org.gradle.api.artifacts.LenientConfiguration.getArtifacts(org.gradle.api.specs.Spec)'`

This error occurred in the Spring Dependency Management plugin when it tried to use a Gradle API method that no longer exists in Gradle 9.6.1.

## Root Causes

### Primary Issue: Plugin Version Incompatibility
The `oauth2-demo` was using plugin versions that were too old for Gradle 9.6.1:
- Spring Boot: 3.1.3 (released Sept 2023) - compiled against older Gradle APIs
- Spring Dependency Management: 1.1.0 - also outdated

### Secondary Issues
- Missing `spring-boot-starter-data-jpa` dependency (caused compilation errors)
- Outdated OAuth2 artifact versions with compatibility issues

## Changes Made

### 1. Updated `oauth2/oauth2-demo/build.gradle`

**Plugins (lines 1-5):**
```gradle
// Before
plugins {
    id 'org.springframework.boot' version '3.1.3'
    id 'io.spring.dependency-management' version '1.1.0'
    id 'java'
}

// After
plugins {
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'java'
}
```

**Dependencies (lines 15-29):**
```gradle
// Added
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

// Updated versions
implementation 'org.springframework.security:spring-security-oauth2-authorization-server:1.3.0'
implementation 'org.springframework.security:spring-security-oauth2-client:6.3.1'
implementation 'org.springframework.security:spring-security-oauth2-resource-server:6.3.1'
```

### 2. Created Documentation Files
- **oauth2/oauth2-demo/GRADLE_FIX_SUMMARY.md** - Detailed technical root cause analysis
- **oauth2/oauth2-demo/BUILD_FIX_CHANGES.md** (this file) - Summary of changes
- Updated **AGENTS.md** - Added item #9 to common pitfalls section

## Verification

The primary Gradle API compatibility issue is now **FIXED**. The build no longer fails with the `LenientConfiguration.getArtifacts()` error.

**Status of Remaining Issues:**
- ✅ Gradle plugin compatibility: FIXED
- ⚠️ Demo application code: Requires updates to use current Spring Security authorization server APIs (separate issue from Gradle build)

## Version Compatibility
- **Gradle:** 9.6.1 ✅
- **Spring Boot:** 3.4.1 (updated from 3.1.3) ✅
- **Spring Dependency Management:** 1.1.6 (updated from 1.1.0) ✅
- **Java:** 17+ (as per project requirements)

## Notes
- This is a **demo/sample application**, not part of the core Spring Security build
- It uses its own repository configuration and is excluded from root project CI checks
- Future Gradle version upgrades should consider updating this demo's plugins proactively to avoid similar issues

