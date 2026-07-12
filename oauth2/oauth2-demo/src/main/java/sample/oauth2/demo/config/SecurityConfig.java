package sample.oauth2.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
		// Default application security chain. Permits public access to authorization
		// endpoints, H2 console and demo DB viewer; requires authentication for others.
		// This chain also enables form login and oauth2Login for the demo UI.
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/", "/error", "/webjars/**", "/login/**", "/oauth2/**", "/.well-known/**",
					"/h2-console/**", "/db/**", "/api/**")
			.permitAll()
			.anyRequest()
			.authenticated())
			.formLogin(Customizer.withDefaults())
			.oauth2Login(Customizer.withDefaults())
			// Enable OAuth2 Resource Server with JWT decoder so that API calls
			// with a Bearer token (e.g. from the React frontend's ProtectedResource
			// tab) are authenticated via the existing JwtDecoder bean.
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			// Enable CORS so the React frontend (localhost:3000) can access protected
			// resources
			.cors(Customizer.withDefaults());

		http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
		http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

		return http.build();
	}

	@Bean
	public UserDetailsService users(PasswordEncoder passwordEncoder) {
		// In-memory users for interactive login during the demo (used on the
		// Authorization Server login page)
		InMemoryUserDetailsManager uds = new InMemoryUserDetailsManager();
		uds.createUser(User.withUsername("user").password(passwordEncoder.encode("password")).roles("USER").build());
		uds.createUser(User.withUsername("admin").password(passwordEncoder.encode("admin")).roles("ADMIN").build());
		return uds;
	}

	@Bean
	public ClientRegistrationRepository clientRegistrationRepository() {
		// In-memory ClientRegistration for the application to act as an OAuth2 client
		// against the local Authorization Server. Values mirror the RegisteredClient
		// stored in the DB.
		// The demo uses this for initiating the authorization_code flow from the app.
		ClientRegistration registration = ClientRegistration.withRegistrationId("demo-client")
			.clientId("demo-client")
			.clientSecret("secret")
			.clientAuthenticationMethod(
					org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("http://localhost:8085/login/oauth2/code/demo-client")
			.redirectUri("http://localhost:3000/callback")
			.scope("openid", "profile")
			.authorizationUri("http://localhost:8085/oauth2/authorize")
			.tokenUri("http://localhost:8085/oauth2/token")
			.jwkSetUri("http://localhost:8085/oauth2/jwks")
			.clientName("Demo Client")
			.build();

		return new InMemoryClientRegistrationRepository(registration);
	}

	@Bean
	public OAuth2AuthorizedClientRepository authorizedClientRepository() {
		// Session-backed repository for authorized OAuth2 clients. Boot normally
		// autoconfigures
		// this once a ClientRegistrationRepository bean is present, but that isn't firing
		// here,
		// so it's declared explicitly (this is the standard implementation for servlet
		// apps).
		return new HttpSessionOAuth2AuthorizedClientRepository();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		// JwtDecoder configured to use the Authorization Server's JWK Set URI so
		// tokens issued by the server can be validated by the client/resource logic.
		return NimbusJwtDecoder.withJwkSetUri("http://localhost:8085/oauth2/jwks").build();
	}

	@Bean
	public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {
		// Register the H2 Console JakartaWebServlet so the /h2-console UI works.
		// Spring Boot 4.1.0-SNAPSHOT (used by this demo) does not include the
		// H2ConsoleAutoConfiguration class, so the spring.h2.console.enabled
		// property is silently ignored — this bean replaces that auto-configuration.
		JakartaWebServlet h2Servlet = new JakartaWebServlet();
		ServletRegistrationBean<JakartaWebServlet> bean = new ServletRegistrationBean<>(h2Servlet, "/h2-console/*");
		bean.setLoadOnStartup(1);
		return bean;
	}

}
