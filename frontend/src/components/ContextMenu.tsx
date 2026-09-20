import React, { useEffect, useRef } from 'react';
import { Share, Star, Edit2, FolderInput, Trash2, Download } from 'lucide-react';
import styles from './ContextMenu.module.css';

interface ContextMenuProps {
  x: number;
  y: number;
  fileId: string;
  isFolder: boolean;
  onClose: () => void;
  onAction: (action: string, id: string) => void;
}

const ContextMenu: React.FC<ContextMenuProps> = ({ x, y, fileId, isFolder, onClose, onAction }) => {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClose]);

  // Prevent menu from overflowing screen
  const safeX = Math.min(x, window.innerWidth - 220);
  const safeY = Math.min(y, window.innerHeight - 300);

  return (
    <div 
      className={styles.contextMenu} 
      style={{ top: safeY, left: safeX }}
      ref={menuRef}
      onContextMenu={(e) => e.preventDefault()}
    >
      <button className={styles.menuItem} onClick={() => onAction('share', fileId)}>
        <Share size={18} className={styles.icon} /> Share
      </button>
      {!isFolder && (
        <button className={styles.menuItem} onClick={() => onAction('download', fileId)}>
          <Download size={18} className={styles.icon} /> Download
        </button>
      )}
      <button className={styles.menuItem} onClick={() => onAction('rename', fileId)}>
        <Edit2 size={18} className={styles.icon} /> Rename
      </button>
      <button className={styles.menuItem} onClick={() => onAction('move', fileId)}>
        <FolderInput size={18} className={styles.icon} /> Move to
      </button>
      <button className={styles.menuItem} onClick={() => onAction('star', fileId)}>
        <Star size={18} className={styles.icon} /> Add to Starred
      </button>
      
      <div className={styles.divider} />
      
      <button className={`${styles.menuItem} ${styles.danger}`} onClick={() => onAction('delete', fileId)}>
        <Trash2 size={18} className={styles.icon} /> Move to trash
      </button>
    </div>
  );
};

export default ContextMenu;
