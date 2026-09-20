import React, { createContext, useContext, useState, type ReactNode } from 'react';
import api from '../services/api';
import axios from 'axios';

export type UploadStatus = 'pending' | 'uploading' | 'success' | 'error';

export interface UploadTask {
  id: string;
  file: File;
  folderId?: string;
  progress: number;
  status: UploadStatus;
  error?: string;
  cancelTokenSource?: any;
}

interface UploadContextProps {
  uploads: UploadTask[];
  uploadFiles: (files: File[], folderId?: string) => void;
  cancelUpload: (id: string) => void;
  removeUpload: (id: string) => void;
}

const UploadContext = createContext<UploadContextProps | undefined>(undefined);

export const UploadProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [uploads, setUploads] = useState<UploadTask[]>([]);

  const uploadFiles = (files: File[], folderId?: string) => {
    const newUploads = files.map(file => ({
      id: Math.random().toString(36).substring(7),
      file,
      folderId,
      progress: 0,
      status: 'pending' as UploadStatus,
      cancelTokenSource: axios.CancelToken.source()
    }));

    setUploads(prev => [...prev, ...newUploads]);

    newUploads.forEach(task => processUpload(task));
  };

  const processUpload = async (task: UploadTask) => {
    setUploads(prev => prev.map(u => u.id === task.id ? { ...u, status: 'uploading' } : u));
    
    try {
      // 1. Get presigned URL
      const { data: { uploadUrl, storageKey } } = await api.get('/files/upload-url', {
        params: {
          filename: task.file.name,
          contentType: task.file.type || 'application/octet-stream',
          folderId: task.folderId
        }
      });

      // 2. Upload directly to storage provider
      await axios.put(uploadUrl, task.file, {
        headers: {
          'Content-Type': task.file.type || 'application/octet-stream'
        },
        cancelToken: task.cancelTokenSource.token,
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setUploads(prev => prev.map(u => u.id === task.id ? { ...u, progress: percentCompleted } : u));
          }
        }
      });

      // 3. Finalize upload with backend
      await api.post('/files/finalize-upload', {
        storageKey,
        filename: task.file.name,
        contentType: task.file.type || 'application/octet-stream',
        size: task.file.size,
        folderId: task.folderId
      });

      setUploads(prev => prev.map(u => u.id === task.id ? { ...u, status: 'success', progress: 100 } : u));
      
      // Dispatch an event so Drive.tsx knows to refresh the file list
      window.dispatchEvent(new CustomEvent('fileUploaded'));

    } catch (err: any) {
      if (axios.isCancel(err)) {
        setUploads(prev => prev.map(u => u.id === task.id ? { ...u, status: 'error', error: 'Cancelled' } : u));
      } else {
        setUploads(prev => prev.map(u => u.id === task.id ? { ...u, status: 'error', error: err.message || 'Upload failed' } : u));
      }
    }
  };

  const cancelUpload = (id: string) => {
    setUploads(prev => {
      const task = prev.find(u => u.id === id);
      if (task && task.status === 'uploading' && task.cancelTokenSource) {
        task.cancelTokenSource.cancel('Upload cancelled by user');
      }
      return prev;
    });
  };

  const removeUpload = (id: string) => {
    setUploads(prev => prev.filter(u => u.id !== id));
  };

  return (
    <UploadContext.Provider value={{ uploads, uploadFiles, cancelUpload, removeUpload }}>
      {children}
    </UploadContext.Provider>
  );
};

export const useUploads = () => {
  const context = useContext(UploadContext);
  if (context === undefined) {
    throw new Error('useUploads must be used within an UploadProvider');
  }
  return context;
};
