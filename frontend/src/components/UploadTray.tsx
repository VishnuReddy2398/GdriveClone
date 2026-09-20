import React, { useState } from 'react';
import { useUploads } from '../contexts/UploadContext';
import styles from './UploadTray.module.css';

const UploadTray: React.FC = () => {
  const { uploads, cancelUpload, removeUpload } = useUploads();
  const [isMinimized, setIsMinimized] = useState(false);

  if (uploads.length === 0) return null;

  const uploadingCount = uploads.filter(u => u.status === 'uploading' || u.status === 'pending').length;
  const hasErrors = uploads.some(u => u.status === 'error');

  return (
    <div className={styles.trayContainer}>
      {/* Header */}
      <div 
        className={`${styles.header} ${hasErrors ? styles.headerError : ''}`}
        onClick={() => setIsMinimized(!isMinimized)}
      >
        <span className={styles.headerTitle}>
          {uploadingCount > 0 
            ? `Uploading ${uploadingCount} item${uploadingCount > 1 ? 's' : ''}`
            : 'Uploads complete'}
        </span>
        <button className={styles.toggleBtn}>
          {isMinimized ? (
            <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" /></svg>
          ) : (
            <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
          )}
        </button>
      </div>

      {/* Upload List */}
      {!isMinimized && (
        <div className={styles.uploadList}>
          {uploads.map(task => (
            <div key={task.id} className={styles.taskItem}>
              <div className={styles.taskTopRow}>
                <span className={styles.taskName} title={task.file.name}>
                  {task.file.name}
                </span>
                <span className={styles.taskStatus}>
                  {task.status === 'uploading' && <span className={styles.statusUploading}>{task.progress}%</span>}
                  {task.status === 'success' && <span className={styles.statusSuccess}>Done</span>}
                  {task.status === 'error' && <span className={styles.statusError}>Failed</span>}
                  {task.status === 'pending' && <span className={styles.statusPending}>Waiting</span>}
                </span>
              </div>
              
              {task.status === 'uploading' && (
                <div className={styles.progressBarContainer}>
                  <div className={styles.progressBarFill} style={{ width: `${task.progress}%` }}></div>
                </div>
              )}
              
              {task.status === 'error' && (
                <div className={styles.errorText} title={task.error}>
                  {task.error}
                </div>
              )}

              <div className={styles.actionRow}>
                {task.status === 'uploading' && (
                  <button onClick={() => cancelUpload(task.id)} className={`${styles.actionBtn} ${styles.btnCancel}`}>Cancel</button>
                )}
                {(task.status === 'success' || task.status === 'error') && (
                  <button onClick={() => removeUpload(task.id)} className={`${styles.actionBtn} ${styles.btnClear}`}>Clear</button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default UploadTray;
