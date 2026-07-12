package sample.oauth2.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
public class DemoController {

	@GetMapping("/")
	public String index() {
		return "OAuth2 demo application — visit /user, /admin, or /api/resource";
	}

	@GetMapping("/user")
	public String user(Principal principal) {
		return "Hello, " + (principal != null ? principal.getName() : "anonymous") + " — this is a protected resource.";
	}

	@GetMapping("/admin")
	public String admin(Principal principal) {
		return "Admin area — principal=" + (principal != null ? principal.getName() : "anonymous");
	}

	@GetMapping("/api/resource")
	public Map<String, Object> apiResource(Principal principal) {
		// Protected API resource accessed via Client Credentials Grant.
		// The principal here is the client_id (e.g., "demo-cc-client"), not a user.
		return Map.of("message", "This is a machine-to-machine protected resource.", "client",
				principal != null ? principal.getName() : "anonymous", "timestamp", java.time.Instant.now().toString());
	}

}
