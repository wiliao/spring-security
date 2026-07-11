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
        String sql = "SELECT id, client_id, client_secret, client_name FROM oauth2_registered_client";
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            return result.isEmpty() ? "No registered clients found." : result;
        } catch (Exception e) {
            return "Error querying registered clients: " + e.getMessage();
        }
    }

    @GetMapping("/authorizations")
    public Object getAuthorizations() {
        String sql = "SELECT id, registered_client_id, principal_name, authorization_grant_type FROM oauth2_authorization";
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            return result.isEmpty() ? "No authorizations found." : result;
        } catch (Exception e) {
            return "Error querying authorizations: " + e.getMessage();
        }
    }

    @GetMapping("/consents")
    public Object getConsents() {
        String sql = "SELECT id, registered_client_id, principal_name, authorities FROM oauth2_authorization_consent";
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            return result.isEmpty() ? "No consents found." : result;
        } catch (Exception e) {
            return "Error querying consents: " + e.getMessage();
        }
    }
}

