import React, { useEffect, useState } from 'react';
import { X, Download, FileText } from 'lucide-react';
import api from '../services/api';
import styles from './FilePreviewModal.module.css';

interface FilePreviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  fileId: string;
  fileName: string;
  mimeType: string;
}

const TEXT_EXTENSIONS = ['.md', '.markdown', '.txt', '.json', '.yml', '.yaml', '.csv', '.xml', '.html', '.css', '.js', '.ts', '.tsx', '.jsx', '.py', '.java', '.c', '.cpp', '.h', '.sh', '.bash', '.zsh', '.env', '.toml', '.ini', '.cfg', '.conf', '.log', '.sql', '.graphql', '.rs', '.go', '.rb', '.php', '.swift', '.kt', '.scala', '.r', '.m'];

const isTextFile = (name: string, mime: string): boolean => {
  if (mime.startsWith('text/') || mime === 'application/json' || mime === 'application/xml') return true;
  const ext = name.lastIndexOf('.') !== -1 ? name.slice(name.lastIndexOf('.')).toLowerCase() : '';
  return TEXT_EXTENSIONS.includes(ext);
};

const FilePreviewModal: React.FC<FilePreviewModalProps> = ({ isOpen, onClose, fileId, fileName, mimeType }) => {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [textContent, setTextContent] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen || !fileId) return;

    const fetchPreview = async () => {
      try {
        if (isTextFile(fileName, mimeType)) {
          const res = await api.get(`/files/${fileId}/download`, { responseType: 'text' });
          setTextContent(typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2));
        } else {
          const res = await api.get(`/files/${fileId}/download`, { responseType: 'blob' });
          const url = URL.createObjectURL(res.data);
          setBlobUrl(url);
        }
      } catch (err) {
        console.error('Failed to load preview', err);
      }
    };

    fetchPreview();

    return () => {
      if (blobUrl) URL.revokeObjectURL(blobUrl);
      setBlobUrl(null);
      setTextContent(null);
    };
  }, [isOpen, fileId]);

  if (!isOpen) return null;

  const handleDownload = () => {
    window.open(`/api/files/${fileId}/download`, '_blank');
  };

  const renderPreview = () => {
    // Image
    if (mimeType.startsWith('image/') && blobUrl) {
      return <img src={blobUrl} alt={fileName} className={styles.previewImage} />;
    }

    // Video
    if (mimeType.startsWith('video/') && blobUrl) {
      return <video src={blobUrl} controls className={styles.previewVideo} />;
    }

    // Audio
    if (mimeType.startsWith('audio/') && blobUrl) {
      return <audio src={blobUrl} controls className={styles.previewAudio} />;
    }

    // PDF
    if (mimeType === 'application/pdf' && blobUrl) {
      return <iframe src={blobUrl} title={fileName} className={styles.previewPdf} />;
    }

    // Text / Code / JSON
    if (textContent !== null) {
      return <pre className={styles.previewText}>{textContent}</pre>;
    }

    // Unsupported
    return (
      <div className={styles.unsupported}>
        <FileText size={64} color="#5f6368" />
        <p>Preview not available for this file type.</p>
        <p style={{ fontSize: '0.875rem' }}>{mimeType}</p>
      </div>
    );
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.header} onClick={(e) => e.stopPropagation()}>
        <span className={styles.fileName}>{fileName}</span>
        <div className={styles.actions}>
          <button className={styles.iconBtn} onClick={handleDownload} title="Download">
            <Download size={20} />
          </button>
          <button className={styles.iconBtn} onClick={onClose} title="Close">
            <X size={20} />
          </button>
        </div>
      </div>
      <div className={styles.body} onClick={(e) => e.stopPropagation()}>
        {renderPreview()}
      </div>
    </div>
  );
};

export default FilePreviewModal;
