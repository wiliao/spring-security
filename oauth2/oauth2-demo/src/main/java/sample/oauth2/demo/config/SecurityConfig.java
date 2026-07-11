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
					"/h2-console/**", "/db/**")
			.permitAll()
			.anyRequest()
			.authenticated()).formLogin(Customizer.withDefaults()).oauth2Login(Customizer.withDefaults());

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

}
