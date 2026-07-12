import React, { useState } from 'react';
import axios from 'axios';
import { Base64 } from 'js-base64';

function ClientCredentialsFlow() {
  const [tokenResponse, setTokenResponse] = useState(null);
  const [apiResponse, setApiResponse] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const requestClientCredentials = async () => {
    try {
      setLoading(true);
      setError(null);
      setTokenResponse(null);
      setApiResponse(null);

      // Encode client_id:client_secret for Basic auth
      const credentials = btoa('demo-cc-client:cc-secret');

      const response = await axios.post('http://localhost:8085/oauth2/token',
        new URLSearchParams({
          grant_type: 'client_credentials',
          scope: 'api:read'
        }),
        {
          headers: {
            'Authorization': `Basic ${credentials}`,
            'Content-Type': 'application/x-www-form-urlencoded'
          }
        }
      );
      setTokenResponse(response.data);
    } catch (err) {
      setError(
        err.response?.data?.error_description
        || err.response?.data?.error
        || err.message
      );
    } finally {
      setLoading(false);
    }
  };

  const callApiResource = async () => {
    if (!tokenResponse?.access_token) return;
    try {
      setError(null);
      const response = await axios.get('http://localhost:8085/api/resource', {
        headers: { 'Authorization': `Bearer ${tokenResponse.access_token}` }
      });
      setApiResponse(response.data);
    } catch (err) {
      setError(err.response?.data?.error || err.message);
    }
  };

  const decodeToken = (token) => {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const payload = JSON.parse(Base64.decode(parts[1]));
      return payload;
    } catch (e) {
      return null;
    }
  };

  const tokenPayload = tokenResponse?.access_token
    ? decodeToken(tokenResponse.access_token)
    : null;

  return (
    <div className="card">
      <h3>Client Credentials Grant — Machine-to-Machine Flow</h3>
      <p>
        This flow is for server-to-server communication. No user interaction is needed.
        The client authenticates with its <code>client_id</code> and <code>client_secret</code>
        and receives an access token directly from the token endpoint.
      </p>

      <div style={{ backgroundColor: '#f0f9ff', padding: '12px', borderRadius: '4px', marginBottom: '16px' }}>
        <p><strong>Client Credentials:</strong></p>
        <p>Client ID: <code>demo-cc-client</code></p>
        <p>Client Secret: <code>cc-secret</code></p>
        <p>Scope: <code>api:read</code></p>
      </div>

      <button onClick={requestClientCredentials} disabled={loading}>
        {loading ? 'Requesting Token...' : 'Get Client Credentials Token'}
      </button>

      {error && (
        <div style={{ color: '#c33', marginTop: '12px', padding: '12px', backgroundColor: '#fee', borderRadius: '4px' }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      {tokenResponse && (
        <div style={{ marginTop: '20px' }}>
          <p><strong>Access Token Received!</strong></p>
          <div className="token-section">{tokenResponse.access_token}</div>

          {tokenPayload && (
            <div style={{ margin: '12px 0' }}>
              <p><strong>Decoded Token:</strong></p>
              <table>
                <tbody>
                  {Object.entries(tokenPayload).map(([key, value]) => (
                    <tr key={key}>
                      <td><strong>{key}:</strong></td>
                      <td>
                        {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p><strong>Token Type:</strong> {tokenResponse.token_type}</p>
          <p><strong>Expires In:</strong> {tokenResponse.expires_in}s</p>
          <p><strong>Scope:</strong> {tokenResponse.scope}</p>

          <button onClick={callApiResource} style={{ backgroundColor: '#27ae60', marginTop: '12px' }}>
            Call /api/resource with this Token
          </button>

          {apiResponse && (
            <div style={{ marginTop: '16px', backgroundColor: '#f0f9ff', padding: '16px', borderRadius: '4px', border: '1px solid #0f4c81' }}>
              <p><strong>API Response:</strong></p>
              <pre style={{ backgroundColor: '#f5f5f5', padding: '12px', borderRadius: '4px', overflow: 'auto' }}>
                {JSON.stringify(apiResponse, null, 2)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default ClientCredentialsFlow;
