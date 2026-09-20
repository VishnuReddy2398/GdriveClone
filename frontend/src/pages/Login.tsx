import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import api from '../services/api';
import { Cloud, ArrowRight, Lock, Mail, KeyRound, Shield, Zap, HardDrive } from 'lucide-react';
import styles from './Login.module.css';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [mfaRequired, setMfaRequired] = useState(false);
  const [mfaCode, setMfaCode] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      const payload: any = { email, password };
      if (mfaRequired && mfaCode) {
        payload.mfaCode = mfaCode;
      }
      const response = await api.post('/auth/login', payload);
      
      // If 202 Accepted, it means MFA is required
      if (response.status === 202 && response.data === 'MFA code required') {
        setMfaRequired(true);
        return;
      }
      
      login(response.data.token, { id: 'unknown', email, mfaEnabled: response.data.mfaEnabled });
      navigate('/drive/my-drive');
    } catch (err: any) {
      const msg = err.response?.data;
      if (err.response?.status === 401 && msg === 'Invalid MFA code') {
        setMfaRequired(true);
        setError('Invalid Authenticator Code.');
      } else {
        setError(msg?.message || msg || 'Login failed. Please check your credentials.');
      }
    }
  };

  return (
    <div className={styles.container}>
      {/* Left Hero Panel */}
      <div className={styles.heroPanel}>
        <div className={styles.heroContent}>
          <div className={styles.heroBrand}>
            <Cloud className={styles.heroBrandIcon} size={32} />
            <span className={styles.heroBrandName}>Drive Clone</span>
          </div>
          <h1 className={styles.heroTitle}>Your files, always within reach.</h1>
          <p className={styles.heroSubtitle}>
            A secure, self-hosted cloud storage platform built for privacy and performance.
          </p>
          <div className={styles.heroFeatures}>
            <div className={styles.heroFeature}>
              <span className={styles.featureIcon}><Shield size={18} /></span>
              <span>End-to-end encryption &amp; 2FA authentication</span>
            </div>
            <div className={styles.heroFeature}>
              <span className={styles.featureIcon}><Zap size={18} /></span>
              <span>Lightning-fast uploads via Cloudflare R2</span>
            </div>
            <div className={styles.heroFeature}>
              <span className={styles.featureIcon}><HardDrive size={18} /></span>
              <span>10 GB free storage with file versioning</span>
            </div>
          </div>
        </div>
      </div>

      {/* Right Form Panel */}
      <div className={styles.formPanel}>
        <div className={styles.formCard}>
          <div className={styles.logoContainer}>
            <h2 className={styles.title}>Welcome back</h2>
            <p className={styles.subtitle}>Sign in to your account to continue.</p>
          </div>
          
          {error && <div className={styles.error}>{error}</div>}
          
          <form onSubmit={handleSubmit} className={styles.form}>
            {!mfaRequired ? (
              <>
                <div className={styles.inputGroup}>
                  <label htmlFor="email">Email</label>
                  <div className={styles.inputWrapper}>
                    <Mail className={styles.inputIcon} size={18} />
                    <input
                      type="email"
                      id="email"
                      placeholder="hello@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                    />
                  </div>
                </div>
                
                <div className={styles.inputGroup}>
                  <label htmlFor="password">Password</label>
                  <div className={styles.inputWrapper}>
                    <Lock className={styles.inputIcon} size={18} />
                    <input
                      type="password"
                      id="password"
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                  </div>
                </div>
              </>
            ) : (
              <div className={styles.inputGroup}>
                <label htmlFor="mfaCode">Authenticator Code</label>
                <div className={styles.inputWrapper}>
                  <KeyRound className={styles.inputIcon} size={18} />
                  <input
                    type="password"
                    id="mfaCode"
                    placeholder="— — —"
                    value={mfaCode}
                    onChange={(e) => setMfaCode(e.target.value.replace(/[^0-9]/g, ''))}
                    maxLength={6}
                    autoFocus
                    required
                  />
                </div>
                <p className={styles.subtitle} style={{marginTop: '0.5rem', textAlign: 'center'}}>
                  Enter the 6-digit code from your authenticator app.
                </p>
              </div>
            )}
            
            <button type="submit" className={styles.button}>
              <span>{mfaRequired ? 'Verify Code' : 'Sign In'}</span>
              <ArrowRight size={18} />
            </button>
          </form>
          
          <div className={styles.footer}>
            <p>Don't have an account? <Link to="/register" className={styles.link}>Register here</Link></p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
