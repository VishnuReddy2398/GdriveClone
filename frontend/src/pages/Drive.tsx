import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useOutletContext, useSearchParams, useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { useUploads } from '../contexts/UploadContext';
import Breadcrumb from '../components/Breadcrumb';
import ContextMenu from '../components/ContextMenu';
import FilePreviewModal from '../components/FilePreviewModal';
import { 
  Folder as FolderIcon, LayoutGrid, List as ListIcon, 
  Image, FileText, Video, Music, Code, MoreVertical,
  CloudUpload, Eye, Download, Link as LinkIcon, X,
  Bolt, ShieldCheck, Play
} from 'lucide-react';
import styles from './Drive.module.css';

interface DriveProps {
  filter?: 'recent' | 'starred' | 'shared';
}

interface FileItem {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  createdAt: any;
}

interface FolderItem {
  id: string;
  name: string;
  createdAt: any;
}

interface BreadcrumbItem {
  id: string | null;
  name: string;
}

const Drive: React.FC<DriveProps> = ({ filter }) => {
  const { folderId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { refreshKey, triggerRefresh } = useOutletContext<any>();
  const { showToast } = useToast();
  const { uploadFiles } = useUploads();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [files, setFiles] = useState<FileItem[]>([]);
  const [folders, setFolders] = useState<FolderItem[]>([]);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [isDragging, setIsDragging] = useState(false);
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [categoryFilter, setCategoryFilter] = useState<'all' | 'images' | 'videos' | 'docs' | 'audio' | 'code'>('all');
  const [inspectorOpen, setInspectorOpen] = useState(true);

  // Context Menu state
  const [contextMenu, setContextMenu] = useState<{ x: number, y: number, id: string, isFolder: boolean } | null>(null);

  // Preview Modal state
  const [previewFile, setPreviewFile] = useState<{ id: string, name: string, mimeType: string } | null>(null);

  const q = searchParams.get('q');

  useEffect(() => {
    fetchContent();
  }, [folderId, filter, q, refreshKey]);

  useEffect(() => {
    setSelectedItems(new Set());
  }, [folderId, filter, q]);

  const fetchContent = async () => {
    setLoading(true);
    try {
      if (q) {
        const res = await api.get('/files/search', { params: { query: q } });
        setFiles(res.data);
        setFolders([]);
        setBreadcrumb([{ id: null, name: 'Search results' }]);
      } else if (filter === 'recent') {
        const res = await api.get('/drive/recent');
        setFiles(res.data);
        setFolders([]);
        setBreadcrumb([{ id: null, name: 'Recent' }]);
      } else if (filter === 'starred') {
        const res = await api.get('/drive/starred');
        setFiles(res.data);
        setFolders([]);
        setBreadcrumb([{ id: null, name: 'Starred' }]);
      } else if (filter === 'shared') {
        const res = await api.get('/permissions/shared-with-me');
        setFiles(res.data.filter((d: any) => d.fileRecord).map((d: any) => d.fileRecord));
        setFolders(res.data.filter((d: any) => d.folder).map((d: any) => d.folder));
        setBreadcrumb([{ id: null, name: 'Shared with me' }]);
      } else {
        const url = folderId ? `/folders/${folderId}` : '/folders';
        const res = await api.get(url);
        
        if (folderId) {
          setFiles(res.data.files || []);
          setFolders(res.data.folders || res.data.subFolders || []);
          
          const path: BreadcrumbItem[] = [{ id: null, name: 'My Drive' }];
          if (res.data.path) {
            res.data.path.forEach((node: any) => {
              path.push({ id: node.id, name: node.name });
            });
          } else {
            const folderName = res.data.currentFolder?.name || res.data.name || 'Folder';
            path.push({ id: folderId, name: folderName });
          }
          setBreadcrumb(path);
        } else {
          setFiles(res.data.files || []);
          setFolders(res.data.folders || []);
          setBreadcrumb([{ id: null, name: 'My Drive' }]);
        }
      }
    } catch (err) {
      showToast("Failed to fetch drive content", "error");
    } finally {
      setLoading(false);
    }
  };

  // Keyboard shortcuts
  const handleKeyDown = useCallback(
    async (e: KeyboardEvent) => {
      if (document.activeElement?.tagName === 'INPUT' || document.activeElement?.tagName === 'TEXTAREA') return;

      if ((e.ctrlKey || e.metaKey) && e.key === 'a') {
        e.preventDefault();
        const allIds = new Set([...folders.map(f => f.id), ...files.map(f => f.id)]);
        setSelectedItems(allIds);
      } else if (e.key === 'Delete' || e.key === 'Backspace') {
        if (selectedItems.size > 0) {
          e.preventDefault();
          try {
            for (const id of selectedItems) {
              const isFolder = folders.some(f => f.id === id);
              const endpoint = isFolder ? `/folders/${id}/trash` : `/files/${id}/trash`;
              await api.post(endpoint);
            }
            showToast(`Moved ${selectedItems.size} item(s) to trash`, 'success');
            setSelectedItems(new Set());
            triggerRefresh();
          } catch (err) {
            showToast("Failed to delete some items", "error");
          }
        }
      } else if (e.key === 'Enter') {
        if (selectedItems.size === 1) {
          e.preventDefault();
          const id = Array.from(selectedItems)[0];
          const folder = folders.find(f => f.id === id);
          if (folder) {
            handleDoubleClick(folder.id, true);
          } else {
            const file = files.find(f => f.id === id);
            if (file) handleDoubleClick(file.id, false, file);
          }
        }
      }
    },
    [selectedItems, folders, files]
  );

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  const [lastClickedId, setLastClickedId] = useState<string | null>(null);

  const handleItemClick = (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    if (e.shiftKey && lastClickedId) {
      const allItems = [...folders.map(f => f.id), ...files.map(f => f.id)];
      const startIdx = allItems.indexOf(lastClickedId);
      const endIdx = allItems.indexOf(id);
      
      if (startIdx !== -1 && endIdx !== -1) {
        const minIdx = Math.min(startIdx, endIdx);
        const maxIdx = Math.max(startIdx, endIdx);
        const range = allItems.slice(minIdx, maxIdx + 1);
        
        if (e.ctrlKey || e.metaKey) {
          setSelectedItems(prev => new Set([...prev, ...range]));
        } else {
          setSelectedItems(new Set(range));
        }
      }
    } else if (e.ctrlKey || e.metaKey) {
      setSelectedItems(prev => {
        const next = new Set(prev);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
      });
      setLastClickedId(id);
    } else {
      setSelectedItems(new Set([id]));
      setLastClickedId(id);
      setInspectorOpen(true);
    }
  };

  const handleContainerClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      setSelectedItems(new Set());
      setLastClickedId(null);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    if (!isDragging) setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  useEffect(() => {
    const handleFileUploaded = () => {
      triggerRefresh();
    };
    window.addEventListener('fileUploaded', handleFileUploaded);
    return () => {
      window.removeEventListener('fileUploaded', handleFileUploaded);
    };
  }, []);

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);

    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const filesArr = Array.from(e.dataTransfer.files);
      uploadFiles(filesArr, folderId || undefined);
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const filesArr = Array.from(e.target.files);
      uploadFiles(filesArr, folderId || undefined);
      e.target.value = '';
    }
  };

  const handleContextMenu = (e: React.MouseEvent, id: string, isFolder: boolean) => {
    e.preventDefault();
    e.stopPropagation();
    if (!selectedItems.has(id)) {
      setSelectedItems(new Set([id]));
    }
    setContextMenu({ x: e.clientX, y: e.clientY, id, isFolder });
  };

  const handleContextAction = async (action: string, id: string) => {
    setContextMenu(null);
    try {
      if (action === 'delete') {
        const endpoint = contextMenu?.isFolder ? `/folders/${id}/trash` : `/files/${id}/trash`;
        await api.post(endpoint);
        showToast("Moved to trash", "success");
        triggerRefresh();
      } else if (action === 'download' && !contextMenu?.isFolder) {
        window.open(`/api/files/${id}/download`, '_blank');
      }
    } catch (err) {
      showToast("Action failed", "error");
    }
  };

  const handleDoubleClick = (id: string, isFolder: boolean, file?: FileItem) => {
    if (isFolder) {
      navigate(`/drive/folders/${id}`);
    } else if (file) {
      setPreviewFile({ id: file.id, name: file.name, mimeType: file.mimeType });
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (dateInput: any) => {
    if (!dateInput) return '--';
    let date;
    if (Array.isArray(dateInput)) {
      date = new Date(
        dateInput[0],
        (dateInput[1] || 1) - 1,
        dateInput[2] || 1,
        dateInput[3] || 0,
        dateInput[4] || 0,
        dateInput[5] || 0
      );
    } else {
      date = new Date(dateInput);
    }

    if (isNaN(date.getTime())) return 'Invalid Date';

    return date.toLocaleDateString('en-US', { 
      month: 'short', 
      day: 'numeric', 
      year: 'numeric' 
    });
  };

  const getFileBadge = (mimeType: string, name: string) => {
    const ext = name.split('.').pop()?.toUpperCase() || 'FILE';
    if (mimeType.startsWith('image/')) return 'PNG';
    if (mimeType.startsWith('video/')) return 'MP4';
    if (mimeType.startsWith('audio/')) return 'MP3';
    if (mimeType.includes('pdf')) return 'PDF';
    if (mimeType.includes('javascript') || mimeType.includes('typescript') || ext === 'TS' || ext === 'JS') return 'TS';
    return ext.slice(0, 4);
  };

  const getFileIcon = (mimeType: string) => {
    if (mimeType.startsWith('image/')) return <Image size={24} color="var(--primary)" />;
    if (mimeType.startsWith('video/')) return <Video size={24} color="var(--tertiary)" />;
    if (mimeType.startsWith('audio/')) return <Music size={24} color="var(--secondary)" />;
    if (mimeType.includes('pdf')) return <FileText size={24} color="var(--error)" />;
    if (mimeType.includes('typescript') || mimeType.includes('javascript') || mimeType.includes('json')) return <Code size={24} color="var(--cyan-accent)" />;
    return <FileText size={24} color="var(--primary)" />;
  };

  // Filtered files according to category chip
  const filteredFiles = files.filter(f => {
    if (categoryFilter === 'all') return true;
    if (categoryFilter === 'images') return f.mimeType.startsWith('image/');
    if (categoryFilter === 'videos') return f.mimeType.startsWith('video/');
    if (categoryFilter === 'audio') return f.mimeType.startsWith('audio/');
    if (categoryFilter === 'docs') return f.mimeType.includes('pdf') || f.mimeType.includes('text') || f.mimeType.includes('document');
    if (categoryFilter === 'code') return f.mimeType.includes('json') || f.mimeType.includes('javascript') || f.mimeType.includes('typescript') || f.name.endsWith('.ts') || f.name.endsWith('.js') || f.name.endsWith('.py');
    return true;
  });

  // Selected file for active inspection drawer
  const selectedFileId = Array.from(selectedItems).find(id => files.some(f => f.id === id));
  const activeFile = files.find(f => f.id === selectedFileId) || (files.length > 0 ? files[0] : null);

  return (
    <div 
      className={`${styles.container} ${isDragging ? styles.dropZoneActive : ''}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      onClick={handleContainerClick}
    >
      {/* 1. Hero Status Strip */}
      <section className={styles.heroBanner}>
        <div className={styles.heroTopRow}>
          <div>
            <div className={styles.clusterStatus}>
              <span className={styles.statusDot} />
              <span>Quantum Node Synced • Cluster 08-Alpha</span>
            </div>
            <h1 className={styles.heroTitle}>AetherDrive Workspace</h1>
            <p className={styles.heroSubtitle}>
              Next-generation encrypted storage shards with zero-latency streaming.
            </p>
          </div>

          <div className={styles.metricsRow}>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Total Assets</span>
              <div className={styles.metricValue}>
                <span>{files.length + folders.length}</span>
                <span className={styles.metricSubtext}>indexed</span>
              </div>
            </div>

            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Throughput</span>
              <div className={styles.metricValue}>
                <span>2.4 GB/s</span>
                <Bolt size={14} color="#34d399" />
              </div>
            </div>

            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Security</span>
              <div className={styles.metricValue}>
                <span>Encrypted</span>
                <ShieldCheck size={14} color="var(--tertiary)" />
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 2. Breadcrumbs & Category Filter Bar */}
      <div className={styles.filterBar}>
        <Breadcrumb path={breadcrumb} />

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          {/* Category Chips */}
          <div className={styles.filterChips}>
            {(['all', 'images', 'videos', 'docs', 'code', 'audio'] as const).map(cat => (
              <button
                key={cat}
                className={`${styles.filterChip} ${categoryFilter === cat ? styles.activeFilterChip : ''}`}
                onClick={() => setCategoryFilter(cat)}
              >
                {cat.charAt(0).toUpperCase() + cat.slice(1)}
              </button>
            ))}
          </div>

          {/* View Toggle */}
          <div className={styles.viewToggle}>
            <button 
              className={`${styles.viewToggleButton} ${viewMode === 'grid' ? styles.activeViewToggle : ''}`}
              onClick={() => setViewMode('grid')}
              title="Grid View"
            >
              <LayoutGrid size={18} />
            </button>
            <button 
              className={`${styles.viewToggleButton} ${viewMode === 'list' ? styles.activeViewToggle : ''}`}
              onClick={() => setViewMode('list')}
              title="List View"
            >
              <ListIcon size={18} />
            </button>
          </div>
        </div>
      </div>

      {/* 3. Drag & Drop Upload Zone */}
      <section 
        className={styles.dropZone}
        onClick={() => fileInputRef.current?.click()}
      >
        <input 
          type="file" 
          ref={fileInputRef} 
          style={{ display: 'none' }} 
          multiple 
          onChange={handleFileInputChange} 
        />
        <div className={styles.dropZoneIconCircle}>
          <CloudUpload size={26} />
        </div>
        <h3 className={styles.dropZoneTitle}>
          Drop files here to upload instantly or click to browse
        </h3>
        <p className={styles.dropZoneSubtitle}>
          Direct quantum encrypted memory pool uploads with automatic verification.
        </p>
        <div className={styles.dropZoneTags}>
          <span className={styles.dropZoneTag}>MP4</span>
          <span className={styles.dropZoneTag}>RAW</span>
          <span className={styles.dropZoneTag}>PDF</span>
          <span className={styles.dropZoneTag}>OBJ</span>
          <span className={styles.dropZoneTag}>TS</span>
        </div>
      </section>

      {/* 4. Smart Folders Section */}
      {folders.length > 0 && (
        <section>
          <div className={styles.sectionHeader}>
            <div className={styles.sectionTitleGroup}>
              <FolderIcon size={20} color="var(--primary)" />
              <h2 className={styles.sectionTitle}>Smart Folders</h2>
              <span className={styles.sectionBadge}>{folders.length}</span>
            </div>
          </div>

          <div className={styles.foldersGrid} style={{ marginTop: '0.85rem' }}>
            {folders.map(folder => {
              const isSelected = selectedItems.has(folder.id);
              return (
                <div 
                  key={folder.id} 
                  className={`${styles.folderCard} ${isSelected ? styles.folderCardSelected : ''}`}
                  onClick={(e) => handleItemClick(e, folder.id)}
                  onContextMenu={(e) => handleContextMenu(e, folder.id, true)}
                  onDoubleClick={() => handleDoubleClick(folder.id, true)}
                >
                  <div className={styles.folderIconWrapper}>
                    <FolderIcon size={22} />
                  </div>
                  <div className={styles.folderInfo}>
                    <div className={styles.folderName}>{folder.name}</div>
                    <div className={styles.folderMeta}>Created {formatDate(folder.createdAt)}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* 5. Main Split View: Files & Inspector Drawer */}
      <section>
        <div className={styles.sectionHeader}>
          <div className={styles.sectionTitleGroup}>
            <FileText size={20} color="var(--secondary)" />
            <h2 className={styles.sectionTitle}>
              {q ? `Search results for "${q}"` : 'Encrypted Assets'}
            </h2>
            <span className={styles.sectionBadge}>{filteredFiles.length} files</span>
          </div>
        </div>

        {loading ? (
          <div className={styles.loading}>
            <span>Accessing quantum shard memory...</span>
          </div>
        ) : filteredFiles.length === 0 && folders.length === 0 ? (
          <div className={styles.emptyState}>
            <FolderIcon size={44} color="var(--text-dim)" />
            <h3 className={styles.emptyStateTitle}>No assets discovered</h3>
            <p className={styles.emptyStateSubtitle}>
              Drag and drop files above or click the + New button to upload.
            </p>
          </div>
        ) : (
          <div 
            className={`${styles.contentSplitView} ${activeFile && inspectorOpen ? styles.contentSplitViewWithDrawer : ''}`}
            style={{ marginTop: '1rem' }}
          >
            {/* File List or Grid */}
            <div>
              {viewMode === 'grid' ? (
                <div className={styles.filesGrid}>
                  {filteredFiles.map(file => {
                    const isSelected = selectedItems.has(file.id);
                    const badge = getFileBadge(file.mimeType, file.name);
                    const isMedia = file.mimeType.startsWith('image/') || file.mimeType.startsWith('video/');

                    return (
                      <div 
                        key={file.id}
                        className={`${styles.fileCard} ${isSelected ? styles.fileCardSelected : ''}`}
                        onClick={(e) => handleItemClick(e, file.id)}
                        onContextMenu={(e) => handleContextMenu(e, file.id, false)}
                        onDoubleClick={() => handleDoubleClick(file.id, false, file)}
                      >
                        {/* Thumbnail View */}
                        <div className={styles.fileThumbnail}>
                          {isMedia ? (
                            <img 
                              src={`/api/media/${file.id}/preview`} 
                              alt={file.name} 
                              className={styles.fileThumbnailImg}
                              onError={(e) => {
                                (e.target as HTMLElement).style.display = 'none';
                              }}
                            />
                          ) : (
                            getFileIcon(file.mimeType)
                          )}
                          <span className={styles.fileBadge}>{badge}</span>

                          {file.mimeType.startsWith('video/') && (
                            <div className={styles.filePlayOverlay}>
                              <div className={styles.filePlayCircle}>
                                <Play size={16} fill="#ffffff" />
                              </div>
                            </div>
                          )}
                        </div>

                        {/* Metadata */}
                        <div className={styles.fileInfo}>
                          <div className={styles.fileNameMeta}>
                            <div className={styles.fileName}>{file.name}</div>
                            <div className={styles.fileMeta}>
                              {formatSize(file.size)} • {formatDate(file.createdAt)}
                            </div>
                          </div>
                          <button 
                            className={styles.fileMoreButton}
                            onClick={(e) => handleContextMenu(e, file.id, false)}
                            title="More options"
                          >
                            <MoreVertical size={16} />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className={styles.filesList}>
                  <div className={styles.listHeader}>
                    <div>Name</div>
                    <div>Last modified</div>
                    <div>Size</div>
                    <div />
                  </div>
                  {filteredFiles.map(file => {
                    const isSelected = selectedItems.has(file.id);
                    return (
                      <div 
                        key={file.id}
                        className={`${styles.listItem} ${isSelected ? styles.listItemSelected : ''}`}
                        onClick={(e) => handleItemClick(e, file.id)}
                        onContextMenu={(e) => handleContextMenu(e, file.id, false)}
                        onDoubleClick={() => handleDoubleClick(file.id, false, file)}
                      >
                        <div className={styles.listItemName}>
                          {getFileIcon(file.mimeType)}
                          <span className={styles.listItemTitle}>{file.name}</span>
                        </div>
                        <div className={styles.listItemMeta}>{formatDate(file.createdAt)}</div>
                        <div className={styles.listItemMeta}>{formatSize(file.size)}</div>
                        <button 
                          className={styles.fileMoreButton}
                          onClick={(e) => handleContextMenu(e, file.id, false)}
                        >
                          <MoreVertical size={16} />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 6. Active File Inspector Drawer (from Stitch) */}
            {activeFile && inspectorOpen && (
              <aside className={styles.inspectorDrawer}>
                <div className={styles.inspectorHeader}>
                  <div className={styles.inspectorTitleGroup}>
                    <Eye size={18} color="var(--primary)" />
                    <span>File Inspector</span>
                  </div>
                  <button 
                    className={styles.inspectorCloseButton}
                    onClick={() => setInspectorOpen(false)}
                    title="Close Inspector"
                  >
                    <X size={18} />
                  </button>
                </div>

                {/* Preview Frame */}
                <div className={styles.inspectorPreviewBox}>
                  {activeFile.mimeType.startsWith('image/') || activeFile.mimeType.startsWith('video/') ? (
                    <img 
                      src={`/api/media/${activeFile.id}/preview`} 
                      alt={activeFile.name} 
                      className={styles.inspectorPreviewImg} 
                    />
                  ) : (
                    getFileIcon(activeFile.mimeType)
                  )}

                  {/* Audio/Video Animated Equalizer Spectrum */}
                  {(activeFile.mimeType.startsWith('audio/') || activeFile.mimeType.startsWith('video/')) && (
                    <div className={styles.spectrumEqualizer}>
                      <div className={styles.spectrumBar} style={{ height: '18px' }} />
                      <div className={styles.spectrumBar} />
                      <div className={styles.spectrumBar} />
                      <div className={styles.spectrumBar} />
                      <div className={styles.spectrumBar} />
                      <div className={styles.spectrumBar} />
                      <div className={styles.spectrumBar} />
                      <div className={styles.spectrumBar} />
                      <span className={styles.spectrumSampling}>48 kHz • 24-bit</span>
                    </div>
                  )}
                </div>

                <div>
                  <h3 className={styles.inspectorFileName}>{activeFile.name}</h3>
                  <p className={styles.inspectorSha}>SHA-256: {activeFile.id.slice(0, 16)}...</p>
                </div>

                {/* Key-Value Details */}
                <div className={styles.inspectorMetaTable}>
                  <div className={styles.inspectorMetaRow}>
                    <span className={styles.inspectorMetaKey}>MIME Type</span>
                    <span className={styles.inspectorMetaValue}>{activeFile.mimeType}</span>
                  </div>
                  <div className={styles.inspectorMetaRow}>
                    <span className={styles.inspectorMetaKey}>Size</span>
                    <span className={styles.inspectorMetaValue}>{formatSize(activeFile.size)}</span>
                  </div>
                  <div className={styles.inspectorMetaRow}>
                    <span className={styles.inspectorMetaKey}>Modified</span>
                    <span className={styles.inspectorMetaValue}>{formatDate(activeFile.createdAt)}</span>
                  </div>
                  <div className={styles.inspectorMetaRow}>
                    <span className={styles.inspectorMetaKey}>Storage Node</span>
                    <span className={styles.inspectorMetaValue} style={{ color: 'var(--primary)' }}>
                      US-East-Quantum-01
                    </span>
                  </div>
                </div>

                {/* Action Buttons */}
                <div className={styles.inspectorActions}>
                  <button 
                    className={styles.inspectorDownloadBtn}
                    onClick={() => window.open(`/api/files/${activeFile.id}/download`, '_blank')}
                  >
                    <Download size={18} />
                    <span>Download Master</span>
                  </button>

                  <div className={styles.inspectorSecondaryActions}>
                    <button 
                      className={styles.inspectorSecondaryBtn}
                      onClick={() => {
                        setPreviewFile({
                          id: activeFile.id,
                          name: activeFile.name,
                          mimeType: activeFile.mimeType
                        });
                      }}
                    >
                      <Eye size={15} />
                      <span>Preview</span>
                    </button>
                    <button 
                      className={styles.inspectorSecondaryBtn}
                      onClick={() => {
                        navigator.clipboard.writeText(`${window.location.origin}/api/files/${activeFile.id}/download`);
                        showToast("Link copied to clipboard", "success");
                      }}
                    >
                      <LinkIcon size={15} />
                      <span>Copy Link</span>
                    </button>
                  </div>
                </div>
              </aside>
            )}
          </div>
        )}
      </section>

      {/* Context Menu */}
      {contextMenu && (
        <ContextMenu 
          x={contextMenu.x} 
          y={contextMenu.y} 
          fileId={contextMenu.id} 
          isFolder={contextMenu.isFolder} 
          onClose={() => setContextMenu(null)}
          onAction={handleContextAction}
        />
      )}

      {/* Fullscreen Preview Modal */}
      {previewFile && (
        <FilePreviewModal
          isOpen={true}
          onClose={() => setPreviewFile(null)}
          fileId={previewFile.id}
          fileName={previewFile.name}
          mimeType={previewFile.mimeType}
        />
      )}
    </div>
  );
};

export default Drive;
