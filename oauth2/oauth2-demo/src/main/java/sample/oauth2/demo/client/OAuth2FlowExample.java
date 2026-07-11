package sample.oauth2.demo.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Example demonstrating how to:
 * 1. Use Spring Security's OAuth2 client to obtain an access token via the authorization code flow
 * 2. Call a protected resource (e.g., /user) with the Bearer token
 *
 * This is a helper component; in a real app you would use this in a scheduled task or API endpoint
 * to demonstrate the end-to-end flow.
 */
@Component
public class OAuth2FlowExample {

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    /**
     * Example: obtain an access token using the client credentials flow or authorization code flow
     * (requires the user to have already authorized via the browser).
     *
     * In a real scenario, you would orchestrate this after the browser redirects back with an auth code.
     */
    public String demonstrateTokenObtention(String clientRegistrationId) {
        try {
            // Create an OAuth2AuthorizedClientManager
            OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                    .authorizationCode()
                    .refreshToken()
                    .clientCredentials()
                    .build();

            DefaultOAuth2AuthorizedClientManager authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
                    clientRegistrationRepository, oAuth2AuthorizedClientRepository);
            authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

            return "OAuth2 Manager configured. In a real app, use this to obtain tokens for client: " + clientRegistrationId;
        } catch (Exception e) {
            return "Error setting up OAuth2 manager: " + e.getMessage();
        }
    }

    /**
     * Example: call a protected resource with a Bearer token.
     * This demonstrates the resource server validation flow.
     */
    public String callProtectedResourceWithToken(String accessToken, String resourceUri) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    resourceUri,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getBody();
        } catch (Exception e) {
            return "Error calling protected resource: " + e.getMessage();
        }
    }
}

