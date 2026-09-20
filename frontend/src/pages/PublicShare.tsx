import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Download, Lock, FileText, AlertCircle } from 'lucide-react';

const API_BASE = '/api/public/links';

const PublicShare: React.FC = () => {
  const { token } = useParams<{ token: string }>();
  const [fileInfo, setFileInfo] = useState<any>(null);
  const [requiresPassword, setRequiresPassword] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchLinkInfo();
  }, [token]);

  const fetchLinkInfo = async () => {
    try {
      const res = await fetch(`${API_BASE}/${token}`);
      if (res.status === 410) {
        setError('This link has expired.');
        setLoading(false);
        return;
      }
      const data = await res.json();
      setFileInfo(data);
      setRequiresPassword(data.requiresPassword);
      if (!data.requiresPassword) {
        setAuthenticated(true);
      }
    } catch {
      setError('Link not found.');
    } finally {
      setLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const res = await fetch(`${API_BASE}/${token}/auth`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password })
      });
      if (res.ok) {
        setAuthenticated(true);
      } else {
        setError('Incorrect password.');
      }
    } catch {
      setError('Authentication failed.');
    }
  };

  const handleDownload = () => {
    window.open(`${API_BASE}/${token}/download`, '_blank');
  };

  const formatSize = (bytes: number) => {
    if (!bytes) return '';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.card}>Loading...</div>
      </div>
    );
  }

  if (error && !fileInfo) {
    return (
      <div style={styles.container}>
        <div style={styles.card}>
          <AlertCircle size={48} color="#d32f2f" />
          <h2 style={styles.title}>{error}</h2>
        </div>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <FileText size={48} color="#1a73e8" />
        <h2 style={styles.title}>{fileInfo?.fileName}</h2>
        <p style={styles.meta}>{fileInfo?.mimeType} · {formatSize(fileInfo?.size)}</p>

        {requiresPassword && !authenticated ? (
          <form onSubmit={handlePasswordSubmit} style={styles.form}>
            <Lock size={20} color="#5f6368" />
            <p style={styles.meta}>This file is password protected.</p>
            {error && <p style={{ color: '#d32f2f', fontSize: '0.875rem' }}>{error}</p>}
            <input
              type="password"
              placeholder="Enter password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              style={styles.input}
              required
            />
            <button type="submit" style={styles.button}>Unlock</button>
          </form>
        ) : (
          <button onClick={handleDownload} style={styles.button}>
            <Download size={18} style={{ marginRight: '0.5rem' }} />
            Download
          </button>
        )}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100vh',
    backgroundColor: '#f0f2f5',
    fontFamily: '-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif',
  },
  card: {
    background: 'white',
    padding: '3rem',
    borderRadius: '12px',
    boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
    textAlign: 'center' as const,
    maxWidth: '420px',
    width: '90%',
  },
  title: {
    fontSize: '1.25rem',
    color: '#1f1f1f',
    margin: '1rem 0 0.5rem',
  },
  meta: {
    color: '#5f6368',
    fontSize: '0.875rem',
    marginBottom: '1.5rem',
  },
  form: {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    gap: '0.75rem',
  },
  input: {
    padding: '0.75rem',
    border: '1px solid #ccc',
    borderRadius: '4px',
    width: '100%',
    fontSize: '0.875rem',
  },
  button: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '0.75rem 1.5rem',
    backgroundColor: '#0b57d0',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    fontSize: '1rem',
    cursor: 'pointer',
    width: '100%',
  },
};

export default PublicShare;
