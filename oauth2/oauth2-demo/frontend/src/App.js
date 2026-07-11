import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Base64 } from 'js-base64';
import AuthorizationFlow from './components/AuthorizationFlow';
import TokenDisplay from './components/TokenDisplay';
import ProtectedResource from './components/ProtectedResource';
import DatabaseViewer from './components/DatabaseViewer';
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
  const tokenEndpoint = 'http://localhost:8085/oauth2/token';
  const resourceEndpoint = 'http://localhost:8085/user';

  // Generate PKCE challenge
  const generatePKCE = () => {
    const codeVerifier = Base64.fromUint8Array(crypto.getRandomValues(new Uint8Array(32))).slice(0, 43);
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    return crypto.subtle.digest('SHA-256', data).then(hashBuffer => {
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const hashBase64 = Base64.fromUint8Array(new Uint8Array(hashArray)).slice(0, 43);
      return { codeVerifier, codeChallenge: hashBase64 };
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

  // Handle callback from authorization server
  useEffect(() => {
    const handleCallback = async () => {
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
            const payload = JSON.parse(Base64.toUint8Array(parts[1]));
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
        <h1>OAuth2 Demo — Authorization Code + PKCE Flow</h1>
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
        <AuthorizationFlow onLogin={handleLogin} />
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
          </div>

          <div className="tab-content">
            {activeTab === 'tokens' && <TokenDisplay accessToken={accessToken} idToken={idToken} />}
            {activeTab === 'resources' && <ProtectedResource accessToken={accessToken} resourceEndpoint={resourceEndpoint} />}
            {activeTab === 'database' && <DatabaseViewer />}
          </div>
        </div>
      )}

      <footer className="footer">
        <p>
          This demo showcases the Authorization Code + PKCE flow.
          See <code>/h2-console</code> for the H2 database console (JDBC URL: <code>jdbc:h2:mem:testdb</code>).
        </p>
      </footer>
    </div>
  );
}

export default App;

