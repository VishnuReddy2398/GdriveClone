import React, { useState, useRef } from 'react';
import { FolderPlus, FileUp, Plus } from 'lucide-react';
import api from '../services/api';
import { useToast } from '../contexts/ToastContext';
import styles from './NewButton.module.css';
import { useUploads } from '../contexts/UploadContext';

interface NewButtonProps {
  currentFolderId?: string;
  onRefresh: () => void;
}

const NewButton: React.FC<NewButtonProps> = ({ currentFolderId, onRefresh }) => {
  const [open, setOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { showToast } = useToast();
  const { uploadFiles } = useUploads();

  const handleNewFolder = async () => {
    setOpen(false);
    const name = window.prompt('Enter folder name:');
    if (!name) return;

    try {
      await api.post('/folders', null, {
        params: { name, parentId: currentFolderId || undefined }
      });
      showToast(`Folder "${name}" created`, 'success');
      onRefresh();
    } catch (err: any) {
      showToast(err.response?.data || 'Failed to create folder', 'error');
    }
  };

  const handleFileUpload = () => {
    setOpen(false);
    fileInputRef.current?.click();
  };

  const onFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const filesArr = Array.from(files);
    uploadFiles(filesArr, currentFolderId || undefined);
    
    // Reset input
    e.target.value = '';
  };

  return (
    <div style={{ position: 'relative' }}>
      <button className={styles.newButton} onClick={() => setOpen(!open)}>
        <Plus className={styles.newIcon} size={20} />
        <span>New</span>
      </button>

      {open && (
        <>
          <div className={styles.dropdownOverlay} onClick={() => setOpen(false)} />
          <div className={styles.dropdown}>
            <button className={styles.menuItem} onClick={handleNewFolder}>
              <FolderPlus size={20} className={styles.menuIcon} />
              New folder
            </button>
            <div className={styles.divider} />
            <button className={styles.menuItem} onClick={handleFileUpload}>
              <FileUp size={20} className={styles.menuIcon} />
              File upload
            </button>
          </div>
        </>
      )}

      <input
        type="file"
        ref={fileInputRef}
        className={styles.hiddenInput}
        multiple
        onChange={onFileSelected}
      />
    </div>
  );
};

export default NewButton;
