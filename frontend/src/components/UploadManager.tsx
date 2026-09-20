import React from 'react';
import { X, ChevronDown } from 'lucide-react';
import styles from './UploadManager.module.css';

export interface UploadTask {
  id: string;
  name: string;
  progress: number;
  status: 'uploading' | 'completed' | 'error';
}

interface UploadManagerProps {
  tasks: UploadTask[];
  onClose: () => void;
}

const UploadManager: React.FC<UploadManagerProps> = ({ tasks, onClose }) => {
  if (tasks.length === 0) return null;

  const activeCount = tasks.filter(t => t.status === 'uploading').length;
  const title = activeCount > 0 ? `Uploading ${activeCount} item${activeCount > 1 ? 's' : ''}` : 'Uploads complete';

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span>{title}</span>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <ChevronDown size={18} style={{ cursor: 'pointer' }} />
          <X size={18} style={{ cursor: 'pointer' }} onClick={onClose} />
        </div>
      </div>
      <div className={styles.list}>
        {tasks.map(task => (
          <div key={task.id} className={styles.item}>
            <div className={styles.itemName}>{task.name}</div>
            <div className={styles.progressBarContainer}>
              <div 
                className={styles.progressBar} 
                style={{ 
                  width: `${task.progress}%`,
                  backgroundColor: task.status === 'error' ? '#d32f2f' : task.status === 'completed' ? '#0f9d58' : '#1a73e8'
                }} 
              />
            </div>
            <div className={styles.status}>
              {task.status === 'uploading' ? `${Math.round(task.progress)}%` : 
               task.status === 'completed' ? 'Done' : 'Error'}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default UploadManager;
