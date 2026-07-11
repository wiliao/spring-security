import React from 'react';

function AuthorizationFlow({ onLogin }) {
  return (
    <div className="main-content">
      <div className="card">
        <h2>OAuth2 Authorization Code Flow with PKCE</h2>
        <p>
          This demo implements the OAuth2 Authorization Code grant with PKCE (Proof Key for Code Exchange).
          Click the button below to initiate the authorization flow.
        </p>
        <p>
          <strong>What happens:</strong>
        </p>
        <ol>
          <li>You will be redirected to the Authorization Server login page</li>
          <li>Enter credentials (user/password) to authenticate</li>
          <li>You'll be asked to consent to the requested scopes</li>
          <li>The Authorization Server returns an authorization code</li>
          <li>The app exchanges the code for an access token using PKCE verification</li>
          <li>You can then access protected resources with the access token</li>
        </ol>
        <p>
          <strong>Demo Credentials:</strong>
          <br />
          Username: <code>user</code>
          <br />
          Password: <code>password</code>
        </p>
        <button onClick={onLogin} style={{ fontSize: '16px', padding: '12px 24px' }}>
          Login with OAuth2
        </button>
      </div>
    </div>
  );
}

export default AuthorizationFlow;

