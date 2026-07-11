import React, { useState, useEffect } from 'react';
import axios from 'axios';

function DatabaseViewer() {
  const [activeTab, setActiveTab] = useState('clients');
  const [clients, setClients] = useState([]);
  const [authorizations, setAuthorizations] = useState([]);
  const [consents, setConsents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);

        const [clientsRes, authRes, consentsRes] = await Promise.all([
          axios.get('/db/clients'),
          axios.get('/db/authorizations'),
          axios.get('/db/consents')
        ]);

        setClients(Array.isArray(clientsRes.data) ? clientsRes.data : []);
        setAuthorizations(Array.isArray(authRes.data) ? authRes.data : []);
        setConsents(Array.isArray(consentsRes.data) ? consentsRes.data : []);
      } catch (err) {
        setError('Error fetching database data: ' + err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const renderTable = (data, columns) => {
    if (!Array.isArray(data) || data.length === 0) {
      return <p>No data available.</p>;
    }

    return (
      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              {columns.map(col => (
                <th key={col}>{col}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, idx) => (
              <tr key={idx}>
                {columns.map(col => (
                  <td key={col}>
                    {typeof row[col] === 'object'
                      ? JSON.stringify(row[col])
                      : String(row[col]).substring(0, 100)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  if (loading) {
    return <div className="card"><p className="loading">Loading database data...</p></div>;
  }

  return (
    <div>
      {error && (
        <div className="card" style={{ color: '#c33', borderLeft: '4px solid #c33' }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
        <button
          onClick={() => setActiveTab('clients')}
          style={{ backgroundColor: activeTab === 'clients' ? '#667eea' : '#ccc' }}
        >
          Registered Clients
        </button>
        <button
          onClick={() => setActiveTab('authorizations')}
          style={{ backgroundColor: activeTab === 'authorizations' ? '#667eea' : '#ccc' }}
        >
          Authorizations
        </button>
        <button
          onClick={() => setActiveTab('consents')}
          style={{ backgroundColor: activeTab === 'consents' ? '#667eea' : '#ccc' }}
        >
          Consents
        </button>
      </div>

      <div className="card">
        {activeTab === 'clients' && (
          <div>
            <h3>Registered OAuth2 Clients</h3>
            {renderTable(clients, ['id', 'client_id', 'client_name'])}
          </div>
        )}

        {activeTab === 'authorizations' && (
          <div>
            <h3>Authorizations</h3>
            {renderTable(authorizations, ['id', 'registered_client_id', 'principal_name', 'authorization_grant_type'])}
          </div>
        )}

        {activeTab === 'consents' && (
          <div>
            <h3>Authorization Consents</h3>
            {renderTable(consents, ['id', 'registered_client_id', 'principal_name', 'authorities'])}
          </div>
        )}
      </div>

      <div className="card" style={{ backgroundColor: '#f9f9f9', border: '1px solid #ddd' }}>
        <p>
          For full SQL access to the H2 database, visit the <a href="/h2-console" target="_blank" rel="noopener noreferrer">H2 Console</a>
          <br />
          <strong>JDBC URL:</strong> <code>jdbc:h2:mem:testdb</code>
          <br />
          <strong>Driver:</strong> <code>org.h2.Driver</code>
        </p>
      </div>
    </div>
  );
}

export default DatabaseViewer;

