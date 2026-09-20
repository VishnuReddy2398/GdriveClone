import React, { useState, useEffect } from 'react';
import { Shield, MonitorSmartphone, KeyRound, ShieldCheck, ShieldOff } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import api from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import styles from './Settings.module.css';

interface Session {
  id: string;
  ipAddress: string;
  userAgent: string;
  createdAt: string;
  lastActiveAt: string;
}

const Settings: React.FC = () => {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);
  const [mfaUri, setMfaUri] = useState<string | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [mfaEnabled, setMfaEnabled] = useState(false);
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();

  // Sync MFA status from auth context
  useEffect(() => {
    if (user) {
      setMfaEnabled(user.mfaEnabled === true);
    }
  }, [user]);

  const fetchSessions = async () => {
    try {
      const response = await api.get('/sessions');
      const sortedSessions = response.data.sort((a: Session, b: Session) => 
        new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime()
      );
      setSessions(sortedSessions);
    } catch (error) {
      showToast('Failed to fetch sessions', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleRevoke = async (id: string) => {
    try {
      await api.post(`/sessions/${id}/revoke`);
      showToast('Session revoked successfully', 'success');
      fetchSessions();
    } catch (error) {
      showToast('Failed to revoke session', 'error');
    }
  };

  const handleSetupMfa = async () => {
    try {
      const response = await api.post('/auth/mfa/setup');
      setMfaUri(response.data.mfaUri);
    } catch (error) {
      showToast('Failed to setup MFA', 'error');
    }
  };

  const handleVerifyMfa = async () => {
    if (!mfaCode || mfaCode.trim().length === 0) {
      showToast('Please enter the 6-digit code from your authenticator app.', 'error');
      return;
    }
    if (mfaCode.trim().length !== 6 || !/^\d{6}$/.test(mfaCode.trim())) {
      showToast('Code must be exactly 6 digits.', 'error');
      return;
    }
    try {
      await api.post('/auth/mfa/verify', { mfaCode: mfaCode.trim() });
      showToast('🎉 Two-Factor Authentication enabled successfully!', 'success');
      setMfaUri(null);
      setMfaCode('');
      setMfaEnabled(true);
      updateUser({ mfaEnabled: true });
    } catch (error) {
      showToast('Invalid MFA code. Please try again.', 'error');
    }
  };

  const handleDisableMfa = async () => {
    try {
      await api.post('/auth/mfa/disable');
      showToast('Two-Factor Authentication has been disabled.', 'success');
      setMfaEnabled(false);
      updateUser({ mfaEnabled: false });
    } catch (error) {
      showToast('Failed to disable MFA.', 'error');
    }
  };

  const getDeviceName = (userAgent: string) => {
    if (userAgent.includes('Windows')) return 'Windows PC';
    if (userAgent.includes('Mac OS')) return 'Mac';
    if (userAgent.includes('Linux')) return 'Linux PC';
    if (userAgent.includes('Android')) return 'Android Device';
    if (userAgent.includes('iPhone') || userAgent.includes('iPad')) return 'iOS Device';
    return 'Unknown Device';
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>
          <Shield size={28} /> Security Settings
        </h1>
        <p className={styles.subtitle}>Manage your account security and active sessions.</p>
      </header>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Active Sessions</h2>
        
        {loading ? (
          <div>Loading sessions...</div>
        ) : (
          <div className={styles.sessionList}>
            {sessions.map((session, index) => (
              <div key={session.id} className={styles.sessionItem}>
                <div className={styles.sessionInfo}>
                  <div className={styles.deviceIcon}>
                    <MonitorSmartphone size={24} />
                  </div>
                  <div className={styles.sessionDetails}>
                    <div className={styles.ipAddress}>
                      {session.ipAddress}
                      {index === 0 && <span className={styles.activeBadge}>Current Session</span>}
                    </div>
                    <div className={styles.userAgent}>
                      {getDeviceName(session.userAgent)} • {new Date(session.lastActiveAt).toLocaleString()}
                    </div>
                  </div>
                </div>
                {index !== 0 && (
                  <button 
                    className={styles.revokeButton} 
                    onClick={() => handleRevoke(session.id)}
                  >
                    Revoke
                  </button>
                )}
              </div>
            ))}
            
            {sessions.length === 0 && (
              <div>No active sessions found.</div>
            )}
          </div>
        )}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Two-Factor Authentication (2FA)</h2>

        {mfaEnabled ? (
          /* ──── MFA IS ENABLED ──── */
          <div style={{ background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.3)', padding: '1.5rem', borderRadius: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
              <ShieldCheck size={28} color="#10b981" />
              <div>
                <div style={{ fontWeight: 600, fontSize: '1.1rem', color: '#10b981' }}>2FA is Enabled</div>
                <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Your account is protected with an authenticator app.</div>
              </div>
            </div>
            <button 
              className={styles.revokeButton}
              style={{ color: '#ef4444', borderColor: '#ef4444', background: 'transparent' }}
              onClick={handleDisableMfa}
            >
              <ShieldOff size={16} style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
              Disable 2FA
            </button>
          </div>
        ) : mfaUri ? (
          /* ──── QR CODE SCANNING STEP ──── */
          <div style={{ background: 'rgba(0,0,0,0.1)', padding: '2rem', borderRadius: '12px', textAlign: 'center' }}>
            <div style={{ background: '#fff', padding: '1rem', display: 'inline-block', borderRadius: '12px', marginBottom: '1.5rem' }}>
              <QRCodeSVG value={mfaUri} size={200} level="M" />
            </div>
            <p className={styles.subtitle} style={{ marginBottom: '1rem' }}>
              Scan this QR code with your Authenticator App (Google Authenticator, Authy, etc.), then enter the 6-digit code below to verify.
            </p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', alignItems: 'center' }}>
              <input
                type="password"
                placeholder="— — —"
                maxLength={6}
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value.replace(/[^0-9]/g, ''))}
                autoFocus
                style={{ padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--border)', background: 'var(--bg-main)', color: 'var(--text-main)', width: '120px', textAlign: 'center', fontSize: '1.25rem', letterSpacing: '4px' }}
              />
              <button 
                className={styles.revokeButton} 
                style={{ color: '#fff', background: '#3b82f6', borderColor: '#3b82f6' }} 
                onClick={handleVerifyMfa}
              >
                Verify & Enable
              </button>
            </div>
          </div>
        ) : (
          /* ──── SETUP BUTTON ──── */
          <div>
            <p className={styles.subtitle} style={{ marginBottom: '1.5rem' }}>
              Add an extra layer of security to your account by enabling 2FA.
            </p>
            <button className={styles.revokeButton} style={{ color: '#fff', background: '#10b981', borderColor: '#10b981' }} onClick={handleSetupMfa}>
              <KeyRound size={18} style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
              Setup 2FA Now
            </button>
          </div>
        )}
      </section>
    </div>
  );
};

export default Settings;
