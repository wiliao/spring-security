package sample.oauth2.demo.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class AuthorizationServerConfig {

	@Bean
	public DataSource dataSource() {
		// Embedded H2 with the default Spring Authorization Server schemas
		// Bootstrap step: creates in-memory DB and executes the Authorization Server
		// schema SQL
		return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
			.addScript("org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql")
			.addScript(
					"org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql")
			.addScript(
					"org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql")
			.build();
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		// JdbcTemplate used by the JDBC-backed RegisteredClient/Authorization
		// repositories
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate,
			PasswordEncoder passwordEncoder) {
		// Programmatic seed of a demo RegisteredClient saved into the JDBC repository at
		// startup
		RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
			.clientId("demo-client")
			.clientSecret(passwordEncoder.encode("secret"))
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			.redirectUri("http://localhost:8085/login/oauth2/code/demo-client")
			.redirectUri("http://localhost:8085/authorized")
			.scope("openid")
			.scope("profile")
			.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
			.clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
			.build();

		// Persist the RegisteredClient so it is available in the
		// `oauth2_registered_client` table
		JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);
		repository.save(registeredClient);
		return repository;
	}

	@Bean
	public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
			RegisteredClientRepository registeredClientRepository) {
		// JDBC-backed OAuth2AuthorizationService persists issued authorizations to the DB
		return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
	}

	@Bean
	public JdbcOAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
			RegisteredClientRepository registeredClientRepository) {
		// JDBC-backed consent service persists end-user consents into the DB
		return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		// Authorization Server security chain (highest precedence). The configurer
		// registers
		// /oauth2/authorize, /oauth2/token, /oauth2/jwks, etc. On this branch,
		// OAuth2AuthorizationServerConfigurer
		// has no static factory method - it's instantiated directly and applied via
		// http.with(...)
		// (the non-deprecated replacement for http.apply(...), since it extends
		// AbstractHttpConfigurer).
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

		http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
			.with(authorizationServerConfigurer, Customizer.withDefaults())
			.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
			// Allow form login for the authorization endpoints (login/consent flows)
			.formLogin(Customizer.withDefaults());

		return http.build();
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		// AuthorizationServerSettings exposes the issuer used in tokens and discovery
		// metadata
		// (replaces the removed ProviderSettings from the old 0.2.x incubator API)
		return AuthorizationServerSettings.builder().issuer("http://localhost:8085").build();
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource() throws Exception {
		// Generate an RSA keypair at startup and expose it as a JWKSet. The Authorization
		// Server
		// uses this JWKSource to serve the /oauth2/jwks endpoint and sign tokens.
		KeyPair keyPair = generateRsaKey();
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey)
			.keyID(UUID.randomUUID().toString())
			.build();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
	}

	private static KeyPair generateRsaKey() throws Exception {
		// Helper that generates a 2048-bit RSA key pair used for token signing
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		return keyPairGenerator.generateKeyPair();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
