package sample.oauth2.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class DemoController {

	@GetMapping("/")
	public String index() {
		return "OAuth2 demo application — visit /user or /admin";
	}

	@GetMapping("/user")
	public String user(Principal principal) {
		return "Hello, " + (principal != null ? principal.getName() : "anonymous") + " — this is a protected resource.";
	}

	@GetMapping("/admin")
	public String admin(Principal principal) {
		return "Admin area — principal=" + (principal != null ? principal.getName() : "anonymous");
	}

}
