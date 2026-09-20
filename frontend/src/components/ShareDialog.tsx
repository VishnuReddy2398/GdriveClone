import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { X } from 'lucide-react';
import styles from './ShareDialog.module.css';

interface ShareDialogProps {
  isOpen: boolean;
  onClose: () => void;
  fileId: string;
  fileName: string;
}

const ShareDialog: React.FC<ShareDialogProps> = ({ isOpen, onClose, fileId, fileName }) => {
  const [email, setEmail] = useState('');
  const [level, setLevel] = useState('VIEWER');
  const [collaborators] = useState<any[]>([]);

  useEffect(() => {
    if (isOpen) {
      // Fetch existing collaborators
      // Mocking fetch as endpoint might vary: api.get(`/permissions/file/${fileId}`)
    }
  }, [isOpen, fileId]);

  if (!isOpen) return null;

  const handleShare = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post(`/permissions/file/${fileId}`, null, {
        params: { targetEmail: email, level }
      });
      setEmail('');
      // Ideally refresh collaborators list here
      alert('Shared successfully!');
    } catch (err: any) {
      alert(err.response?.data || 'Failed to share');
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.dialog}>
        <div className={styles.header}>
          <h2 className={styles.title}>Share "{fileName}"</h2>
          <button className={styles.closeButton} onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleShare} className={styles.inputGroup}>
          <input
            type="email"
            placeholder="Add people via email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={styles.input}
            required
          />
          <select 
            value={level} 
            onChange={(e) => setLevel(e.target.value)}
            className={styles.select}
          >
            <option value="VIEWER">Viewer</option>
            <option value="COMMENTER">Commenter</option>
            <option value="EDITOR">Editor</option>
          </select>
          <button type="submit" className={styles.button}>Send</button>
        </form>

        <div className={styles.list}>
          <h3 style={{ fontSize: '0.875rem', color: '#5f6368', marginBottom: '0.5rem' }}>People with access</h3>
          {collaborators.length === 0 ? (
            <div className={styles.userEmail} style={{ color: '#5f6368' }}>No collaborators yet.</div>
          ) : (
            collaborators.map((c, i) => (
              <div key={i} className={styles.listItem}>
                <span className={styles.userEmail}>{c.user.email}</span>
                <span className={styles.userRole}>{c.level.toLowerCase()}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default ShareDialog;
