import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { Base64 } from 'js-base64';
import AuthorizationFlow from './components/AuthorizationFlow';
import TokenDisplay from './components/TokenDisplay';
import ProtectedResource from './components/ProtectedResource';
import DatabaseViewer from './components/DatabaseViewer';
import ClientCredentialsFlow from './components/ClientCredentialsFlow';
import './App.css';

function App() {
  const [accessToken, setAccessToken] = useState(null);
  const [refreshToken, setRefreshToken] = useState(null);
  const [idToken, setIdToken] = useState(null);
  const [principal, setPrincipal] = useState(null);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('login');

  // OAuth2 configuration
  const clientId = 'demo-client';
  const clientSecret = 'secret';
  const redirectUri = 'http://localhost:3000/callback';
  const authorizationEndpoint = 'http://localhost:8085/oauth2/authorize';
  // Use absolute URLs for AJAX calls. CORS is configured on the backend
  // (AuthorizationServerConfig.corsConfigurationSource()) to allow localhost:3000.
  // This avoids CRA proxy issues with cookie forwarding and response wrapping.
  const tokenEndpoint = 'http://localhost:8085/oauth2/token';
  const resourceEndpoint = 'http://localhost:8085/user';

  // Generate PKCE challenge
  const generatePKCE = () => {
    // Generate random bytes and convert to Base64URL (RFC 7636)
    const base64UrlEncode = (buffer) => {
      return Base64.fromUint8Array(new Uint8Array(buffer))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
    };
    const codeVerifier = base64UrlEncode(crypto.getRandomValues(new Uint8Array(32)));
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    return crypto.subtle.digest('SHA-256', data).then(hashBuffer => {
      const codeChallenge = base64UrlEncode(hashBuffer);
      return { codeVerifier, codeChallenge };
    });
  };

  // Initiate authorization flow
  const handleLogin = async () => {
    try {
      setError(null);
      const { codeVerifier, codeChallenge } = await generatePKCE();

      // Store code verifier in session storage for later use in token exchange
      sessionStorage.setItem('pkce_code_verifier', codeVerifier);

      // Redirect to authorization server
      const params = new URLSearchParams({
        response_type: 'code',
        client_id: clientId,
        redirect_uri: redirectUri,
        scope: 'openid profile',
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state: 'random-state-' + Math.random().toString(36).substring(7)
      });

      window.location.href = `${authorizationEndpoint}?${params.toString()}`;
    } catch (err) {
      setError('Error initiating login: ' + err.message);
    }
  };

  // Prevent duplicate execution in React StrictMode (development mode double-invocation)
  const tokenExchangeStarted = useRef(false);

  // Handle callback from authorization server
  useEffect(() => {
    const handleCallback = async () => {
      // Guard: prevent duplicate execution from React StrictMode double-invocation
      if (tokenExchangeStarted.current) {
        return;
      }
      tokenExchangeStarted.current = true;

      const params = new URLSearchParams(window.location.search);
      const code = params.get('code');

      if (code) {
        try {
          setError(null);
          const codeVerifier = sessionStorage.getItem('pkce_code_verifier');

          if (!codeVerifier) {
            throw new Error('Code verifier not found. Session may have expired.');
          }

          // Exchange authorization code for tokens
          const tokenData = new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: redirectUri,
            client_id: clientId,
            client_secret: clientSecret,
            code_verifier: codeVerifier
          });

          const response = await axios.post(tokenEndpoint, tokenData, {
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded'
            }
          });

          setAccessToken(response.data.access_token);
          setRefreshToken(response.data.refresh_token || null);
          setIdToken(response.data.id_token || null);
          setActiveTab('resources');

          // Clear URL
          window.history.replaceState({}, document.title, window.location.pathname);
          sessionStorage.removeItem('pkce_code_verifier');

          // Decode principal from ID token or access token
          if (response.data.id_token) {
            const parts = response.data.id_token.split('.');
            // Use Base64.decode() which returns a string, NOT Base64.toUint8Array()
            // which returns a Uint8Array. Passing a Uint8Array to JSON.parse() would
            // cause it to call toString() on it, producing comma-separated byte numbers
            // like "255,34,56,..." which is not valid JSON.
            const payload = JSON.parse(Base64.decode(parts[1]));
            setPrincipal(payload);
          }
        } catch (err) {
          setError('Token exchange failed: ' + (err.response?.data?.error_description || err.message));
        }
      }
    };

    handleCallback();
  }, []);

  const handleLogout = () => {
    setAccessToken(null);
    setRefreshToken(null);
    setIdToken(null);
    setPrincipal(null);
    setError(null);
    setActiveTab('login');
    sessionStorage.removeItem('pkce_code_verifier');
  };

  return (
    <div className="app">
      <header className="header">
        <h1>OAuth2 Demo — Authorization Code + PKCE &amp; Client Credentials</h1>
        {accessToken && (
          <div className="logged-in-info">
            <span>Logged in as: {principal?.name || principal?.sub || 'User'}</span>
            <button onClick={handleLogout} className="logout-btn">Logout</button>
          </div>
        )}
      </header>

      {error && (
        <div className="error-box">
          <strong>Error:</strong> {error}
        </div>
      )}

      {!accessToken ? (
        <div className="main-content">
          <AuthorizationFlow onLogin={handleLogin} />
          <hr style={{ margin: '40px 0', border: 'none', borderTop: '2px dashed #ddd' }} />
          <ClientCredentialsFlow />
        </div>
      ) : (
        <div className="tabs">
          <div className="tab-buttons">
            <button
              className={`tab-btn ${activeTab === 'tokens' ? 'active' : ''}`}
              onClick={() => setActiveTab('tokens')}
            >
              Tokens
            </button>
            <button
              className={`tab-btn ${activeTab === 'resources' ? 'active' : ''}`}
              onClick={() => setActiveTab('resources')}
            >
              Protected Resource
            </button>
            <button
              className={`tab-btn ${activeTab === 'database' ? 'active' : ''}`}
              onClick={() => setActiveTab('database')}
            >
              Database
            </button>
            <button
              className={`tab-btn ${activeTab === 'client-credentials' ? 'active' : ''}`}
              onClick={() => setActiveTab('client-credentials')}
            >
              Client Credentials
            </button>
          </div>

          <div className="tab-content">
            {activeTab === 'tokens' && <TokenDisplay accessToken={accessToken} idToken={idToken} />}
            {activeTab === 'resources' && <ProtectedResource accessToken={accessToken} resourceEndpoint={resourceEndpoint} />}
            {activeTab === 'database' && <DatabaseViewer />}
            {activeTab === 'client-credentials' && <ClientCredentialsFlow />}
          </div>
        </div>
      )}

      <footer className="footer">
        <p>
          This demo showcases both the Authorization Code + PKCE flow and
          the Client Credentials grant. See <code>/h2-console</code> for the
          H2 database console (JDBC URL: <code>jdbc:h2:mem:testdb</code>).
        </p>
      </footer>
    </div>
  );
}

export default App;
