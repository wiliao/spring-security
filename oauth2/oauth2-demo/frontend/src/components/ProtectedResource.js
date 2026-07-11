import React, { useState } from 'react';
import axios from 'axios';

function ProtectedResource({ accessToken, resourceEndpoint }) {
  const [resourceData, setResourceData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const callResource = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await axios.get(resourceEndpoint, {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Accept': 'application/json'
        }
      });

      setResourceData(response.data);
    } catch (err) {
      setError(
        err.response?.status === 401
          ? 'Unauthorized: Token may have expired or is invalid'
          : `Error: ${err.response?.data?.error || err.message}`
      );
      setResourceData(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h3>Protected Resource Access</h3>
      <p>
        Use your access token to call a protected resource endpoint.
        The access token is sent in the <code>Authorization</code> header as a Bearer token.
      </p>

      <p><strong>Endpoint:</strong> <code>{resourceEndpoint}</code></p>
      <p><strong>Authorization Header:</strong> <code>Bearer {accessToken?.substring(0, 50)}...</code></p>

      <button onClick={callResource} disabled={loading}>
        {loading ? 'Loading...' : 'Call Protected Resource'}
      </button>

      {error && (
        <div style={{ color: '#c33', marginTop: '12px', padding: '12px', backgroundColor: '#fee', borderRadius: '4px' }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      {resourceData && (
        <div style={{ marginTop: '20px', backgroundColor: '#f0f9ff', padding: '16px', borderRadius: '4px', border: '1px solid #0f4c81' }}>
          <p><strong>Response:</strong></p>
          <pre style={{ backgroundColor: '#f5f5f5', padding: '12px', borderRadius: '4px', overflow: 'auto' }}>
            {typeof resourceData === 'object' ? JSON.stringify(resourceData, null, 2) : resourceData}
          </pre>
        </div>
      )}
    </div>
  );
}

export default ProtectedResource;

