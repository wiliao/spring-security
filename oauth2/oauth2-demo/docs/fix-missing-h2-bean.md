# Fix: Missing H2 Console Servlet Bean

## Problem

The H2 Console web UI (`/h2-console`) did not work despite having the standard properties in `application.properties`:

```properties
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

These properties were silently ignored — no servlet was registered, and navigating to `http://localhost:8085/h2-console` returned 404.

## Root Cause

Spring Boot auto-configures the H2 Console via `H2ConsoleAutoConfiguration`. However, **this class does not exist** in `spring-boot-autoconfigure-4.1.0-SNAPSHOT` — the version used by this demo.

The auto-config JAR contains only 12 top-level auto-configuration classes (core Spring/Boot infrastructure), none of which are H2-related. The `spring.h2.console.*` properties bind to `H2ConsoleProperties` which also doesn't exist, so Spring simply ignores them.

This is consistent with the general theme of this demo: it uses an early 4.1.0-SNAPSHOT that omits many web/DB auto-configuration modules.

## Solution

Register the H2 Console servlet manually via a `@Bean` in `SecurityConfig.java`:

```java
import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

@Bean
public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {
    JakartaWebServlet h2Servlet = new JakartaWebServlet();
    ServletRegistrationBean<JakartaWebServlet> bean =
        new ServletRegistrationBean<>(h2Servlet, "/h2-console/*");
    bean.setLoadOnStartup(1);
    return bean;
}
```

Key details:

- Uses `JakartaWebServlet` (not the older `WebServlet`) — H2 2.4.240 ships both, but this project runs on Tomcat 11 + Spring Boot 4.1 (Jakarta Servlet 6), so the `jakarta.servlet.Servlet` variant is required.
- The existing security configuration in `defaultSecurityFilterChain` already permits `/h2-console/**`, disables CSRF on it, and removes `X-Frame-Options` — no changes needed there.
- The unused `spring.h2.console.*` properties were removed from `application.properties` to avoid confusion.

## Files Changed

| File | Change |
|---|---|
| `src/main/resources/application.properties` | Removed `spring.h2.console.enabled` and `spring.h2.console.path` (silently ignored) |
| `src/main/java/.../config/SecurityConfig.java` | Added `h2ConsoleServlet()` bean |

## Verification

1. Start the app: `./gradlew :oauth2-demo:bootRun`
2. Open `http://localhost:8085/h2-console`
3. Login with JDBC URL `jdbc:h2:mem:testdb`, user `sa`, blank password
4. Confirm you can browse tables (`oauth2_registered_client`, `oauth2_authorization`, etc.)
