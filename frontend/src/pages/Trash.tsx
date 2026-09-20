import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { Trash2, RotateCcw } from 'lucide-react';
import styles from './Drive.module.css';

const Trash: React.FC = () => {
  const [files, setFiles] = useState<any[]>([]);
  const [folders, setFolders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchTrash();
  }, []);

  const fetchTrash = async () => {
    try {
      const res = await api.get('/trash');
      setFiles(res.data.files || []);
      setFolders(res.data.folders || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const emptyTrash = async () => {
    if (window.confirm('Are you sure you want to permanently delete all items in the trash? This cannot be undone.')) {
      try {
        await api.delete('/trash/empty');
        fetchTrash();
      } catch (err) {
        console.error(err);
      }
    }
  };

  if (loading) return <div className={styles.loading}>Loading Trash...</div>;

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>Trash</h1>
        <button 
          onClick={emptyTrash} 
          disabled={files.length === 0 && folders.length === 0}
          style={{ padding: '0.5rem 1rem', background: '#d32f2f', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
        >
          Empty Trash
        </button>
      </header>

      {files.length === 0 && folders.length === 0 ? (
        <div className={styles.emptyState}>
          <Trash2 size={48} color="#dadce0" />
          <p>No items in trash.</p>
        </div>
      ) : (
        <div className={styles.list}>
          <div className={styles.listHeader}>
            <div>Name</div>
            <div>Date deleted</div>
            <div>Actions</div>
          </div>
          
          {/* Render folders... */}
          {folders.map(f => (
            <div key={f.id} className={styles.itemListItem}>
              <div>{f.name}</div>
              <div>{f.deletedAt}</div>
              <div><RotateCcw size={16} /> Restore</div>
            </div>
          ))}

          {/* Render files... */}
          {files.map(f => (
            <div key={f.id} className={styles.itemListItem}>
              <div>{f.name}</div>
              <div>{f.deletedAt}</div>
              <div><RotateCcw size={16} /> Restore</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Trash;
