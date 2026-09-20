import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import api from '../services/api';
import { Cloud, ArrowRight, Lock, Mail, User, ShieldCheck, Shield, Zap, HardDrive } from 'lucide-react';
import styles from './Login.module.css';

const Register = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [mfaUri, setMfaUri] = useState<string | null>(null);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      const response = await api.post('/auth/register', { email, password, name });
      if (response.data?.mfaUri) {
        setMfaUri(response.data.mfaUri);
      } else {
        navigate('/login');
      }
    } catch (err: any) {
      setError(err.response?.data?.message || err.response?.data || 'Registration failed.');
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
          <h1 className={styles.heroTitle}>Start your secure cloud journey.</h1>
          <p className={styles.heroSubtitle}>
            Create an account and get 10 GB of encrypted storage — completely free.
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
            <h2 className={styles.title}>Create an Account</h2>
            <p className={styles.subtitle}>Join us and start organizing your files.</p>
          </div>
          
          {error && <div className={styles.error}>{error}</div>}
          
          {!mfaUri ? (
          <form onSubmit={handleSubmit} className={styles.form}>
            <div className={styles.inputGroup}>
              <label>Name</label>
              <div className={styles.inputWrapper}>
                <User className={styles.inputIcon} size={18} />
                <input 
                  type="text" 
                  placeholder="John Doe"
                  value={name} 
                  onChange={(e) => setName(e.target.value)} 
                  required 
                />
              </div>
            </div>
            
            <div className={styles.inputGroup}>
              <label>Email</label>
              <div className={styles.inputWrapper}>
                <Mail className={styles.inputIcon} size={18} />
                <input 
                  type="email" 
                  placeholder="hello@example.com"
                  value={email} 
                  onChange={(e) => setEmail(e.target.value)} 
                  required 
                />
              </div>
            </div>
            
            <div className={styles.inputGroup}>
              <label>Password</label>
              <div className={styles.inputWrapper}>
                <Lock className={styles.inputIcon} size={18} />
                <input 
                  type="password" 
                  placeholder="••••••••"
                  value={password} 
                  onChange={(e) => setPassword(e.target.value)} 
                  required 
                />
              </div>
            </div>
            
            <button type="submit" className={styles.button}>
              <span>Register</span>
              <ArrowRight size={18} />
            </button>
          </form>
          ) : (
            <div className={styles.qrContainer}>
              <ShieldCheck size={48} color="#10b981" />
              <div className={styles.qrText}>
                <strong>Registration successful!</strong>
                <br/><br/>
                To complete your account setup, you must enable Two-Factor Authentication. 
                Scan this QR code with Google Authenticator, Authy, or your preferred authenticator app.
              </div>
              
              <div className={styles.qrCodeWrapper}>
                <QRCodeSVG value={mfaUri} size={200} level="M" />
              </div>

              <button onClick={() => navigate('/login')} className={styles.button}>
                <span>I have scanned the code. Login now</span>
                <ArrowRight size={18} />
              </button>
            </div>
          )}
          
          <div className={styles.footer}>
            <p>Already have an account? <Link to="/login" className={styles.link}>Sign in here</Link></p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;
