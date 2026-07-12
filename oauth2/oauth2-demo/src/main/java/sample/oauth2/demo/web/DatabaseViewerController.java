package sample.oauth2.demo.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/db")
public class DatabaseViewerController {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@GetMapping("/clients")
	public Object getRegisteredClients() {
		// Use double-quoted aliases to preserve lowercase column names in the JSON
		// response. H2 uppercases unquoted identifiers, but the frontend accesses
		// these fields as lowercase (e.g., row['id'], row['client_id']).
		String sql = "SELECT id AS \"id\", client_id AS \"client_id\", client_secret AS \"client_secret\", client_name AS \"client_name\" FROM oauth2_registered_client";
		try {
			List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
			return result.isEmpty() ? "No registered clients found." : result;
		}
		catch (Exception e) {
			return "Error querying registered clients: " + e.getMessage();
		}
	}

	@GetMapping("/authorizations")
	public Object getAuthorizations() {
		String sql = "SELECT id AS \"id\", registered_client_id AS \"registered_client_id\", principal_name AS \"principal_name\", authorization_grant_type AS \"authorization_grant_type\" FROM oauth2_authorization";
		try {
			List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
			return result.isEmpty() ? "No authorizations found." : result;
		}
		catch (Exception e) {
			return "Error querying authorizations: " + e.getMessage();
		}
	}

	@GetMapping("/consents")
	public Object getConsents() {
		// Note: oauth2_authorization_consent has NO 'id' column;
		// its primary key is a composite (registered_client_id, principal_name).
		String sql = "SELECT registered_client_id AS \"registered_client_id\", principal_name AS \"principal_name\", authorities AS \"authorities\" FROM oauth2_authorization_consent";
		try {
			List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
			return result.isEmpty() ? "No consents found." : result;
		}
		catch (Exception e) {
			return "Error querying consents: " + e.getMessage();
		}
	}

}
