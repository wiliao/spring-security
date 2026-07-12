import React from 'react';
import { Base64 } from 'js-base64';

function TokenDisplay({ accessToken, idToken }) {
  const decodeToken = (token) => {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      // Use Base64.decode() which returns a string, not Base64.toUint8Array()
      // which returns a Uint8Array. Passing a Uint8Array to JSON.parse() would
      // cause it to call toString() on it, producing comma-separated byte numbers
      // like "255,34,56,..." which is not valid JSON.
      const payload = JSON.parse(Base64.decode(parts[1]));
      return payload;
    } catch (e) {
      return null;
    }
  };

  const accessTokenPayload = accessToken ? decodeToken(accessToken) : null;
  const idTokenPayload = idToken ? decodeToken(idToken) : null;

  const formatDate = (timestamp) => {
    return new Date(timestamp * 1000).toLocaleString();
  };

  return (
    <div>
      <div className="card">
        <h3>Access Token</h3>
        <p><strong>Raw Token (JWT):</strong></p>
        <div className="token-section">{accessToken}</div>

        {accessTokenPayload && (
          <div>
            <p><strong>Decoded Payload:</strong></p>
            <table>
              <tbody>
                {Object.entries(accessTokenPayload).map(([key, value]) => (
                  <tr key={key}>
                    <td><strong>{key}:</strong></td>
                    <td>
                      {typeof value === 'object' ? JSON.stringify(value) :
                       key.includes('exp') || key.includes('iat') ? formatDate(value) :
                       String(value)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {idToken && (
        <div className="card">
          <h3>ID Token (OIDC)</h3>
          <p><strong>Raw Token (JWT):</strong></p>
          <div className="token-section">{idToken}</div>

          {idTokenPayload && (
            <div>
              <p><strong>Decoded Payload:</strong></p>
              <table>
                <tbody>
                  {Object.entries(idTokenPayload).map(([key, value]) => (
                    <tr key={key}>
                      <td><strong>{key}:</strong></td>
                      <td>
                        {typeof value === 'object' ? JSON.stringify(value) :
                         key.includes('exp') || key.includes('iat') || key.includes('auth_time') ? formatDate(value) :
                         String(value)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default TokenDisplay;
