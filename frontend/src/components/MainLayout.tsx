import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useWebSocket } from '../contexts/WebSocketContext';
import { 
  Cloud, HardDrive, Users, Clock, Star, Trash2, 
  Search, Menu, Bell, X, Shield, Settings,
  LogOut, Database
} from 'lucide-react';
import NewButton from './NewButton';
import styles from './MainLayout.module.css';

const MainLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const { notifications } = useWebSocket();
  const navigate = useNavigate();
  const { folderId } = useParams();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [hideMfaBanner, setHideMfaBanner] = useState(false);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/drive/my-drive?q=${encodeURIComponent(searchQuery.trim())}`);
    }
  };

  const triggerRefresh = () => setRefreshKey(prev => prev + 1);

  const displayName = user?.email?.split('@')[0] || 'Elena Rostova';
  const displayInitial = (displayName[0] || 'E').toUpperCase();

  return (
    <div className={styles.layout}>
      {/* ATMOSPHERIC GLOW BACKDROP */}
      <div className={styles.atmosphericBackdrop}>
        <div className="bg-halo-purple" />
        <div className="bg-halo-blue" />
        <div className="bg-halo-pink" />
        <div className="bg-grid-dots" />
      </div>

      {/* Mobile overlay */}
      {sidebarOpen && (
        <div className={styles.mobileOverlay} onClick={() => setSidebarOpen(false)} />
      )}

      {/* Frosted Glass Sidebar */}
      <aside className={`${styles.sidebar} ${sidebarOpen ? styles.sidebarOpen : ''}`}>
        {/* Brand Header */}
        <div className={styles.logo}>
          <div className={styles.logoIconWrapper}>
            <Cloud size={22} color="#ffffff" strokeWidth={2.5} />
          </div>
          <div className={styles.logoTextContainer}>
            <div className={styles.logoTitleRow}>
              <span className={styles.logoText}>AetherDrive</span>
              <span className={styles.versionBadge}>v2.4</span>
            </div>
            <span className={styles.logoSubtitle}>Quantum Storage</span>
          </div>
          <button 
            className={styles.closeSidebar} 
            onClick={() => setSidebarOpen(false)}
            aria-label="Close sidebar"
          >
            <X size={20} />
          </button>
        </div>

        {/* Action Button Container */}
        <div className={styles.newButtonContainer}>
          <NewButton currentFolderId={folderId} onRefresh={triggerRefresh} />
        </div>

        {/* Navigation Links */}
        <nav className={styles.nav}>
          <NavLink 
            to="/drive/my-drive" 
            className={({ isActive }) => isActive ? `${styles.navItem} ${styles.activeNavItem}` : styles.navItem}
          >
            <HardDrive className={styles.navIcon} size={19} />
            <span>My Drive</span>
          </NavLink>

          <NavLink 
            to="/drive/shared" 
            className={({ isActive }) => isActive ? `${styles.navItem} ${styles.activeNavItem}` : styles.navItem}
          >
            <Users className={styles.navIcon} size={19} />
            <span>Shared with me</span>
          </NavLink>

          <NavLink 
            to="/drive/recent" 
            className={({ isActive }) => isActive ? `${styles.navItem} ${styles.activeNavItem}` : styles.navItem}
          >
            <Clock className={styles.navIcon} size={19} />
            <span>Recent</span>
          </NavLink>

          <NavLink 
            to="/drive/starred" 
            className={({ isActive }) => isActive ? `${styles.navItem} ${styles.activeNavItem}` : styles.navItem}
          >
            <Star className={styles.navIcon} size={19} />
            <span>Starred</span>
          </NavLink>

          <NavLink 
            to="/drive/trash" 
            className={({ isActive }) => isActive ? `${styles.navItem} ${styles.activeNavItem}` : styles.navItem}
          >
            <Trash2 className={styles.navIcon} size={19} />
            <span>Trash</span>
          </NavLink>

          <NavLink 
            to="/settings" 
            className={({ isActive }) => isActive ? `${styles.navItem} ${styles.activeNavItem}` : styles.navItem}
          >
            <Shield className={styles.navIcon} size={19} />
            <span>Security &amp; 2FA</span>
          </NavLink>
        </nav>

        {/* Bottom Sidebar: Storage Widget & User Card */}
        <div style={{ marginTop: 'auto', paddingTop: '1rem' }}>
          {/* Storage Meter Widget */}
          <div className={styles.storageWidget}>
            <div className={styles.storageHeader}>
              <div className={styles.storageTitleGroup}>
                <Database size={15} color="var(--primary)" />
                <span>Quantum Storage</span>
              </div>
              <span className={styles.storagePercentage}>74%</span>
            </div>
            <div className={styles.storageProgressBar}>
              <div className={styles.storageProgressFill} style={{ width: '74%' }} />
            </div>
            <div className={styles.storageDetails}>
              <span>742.8 GB / 1 TB</span>
              <span className={styles.upgradeLink}>Upgrade</span>
            </div>
          </div>

          {/* User Profile Card */}
          <div className={styles.userProfileCard}>
            <div className={styles.userProfileInfo}>
              <div className={styles.avatarWrapper}>
                <div className={styles.avatarFallback}>{displayInitial}</div>
                <span className={styles.onlineStatusIndicator} />
              </div>
              <div className={styles.userNameContainer}>
                <div className={styles.userName}>{displayName}</div>
                <div className={styles.userTier}>Quantum Tier Member</div>
              </div>
            </div>
            <button 
              className={styles.profileSettingsButton}
              onClick={() => navigate('/settings')}
              title="Settings"
            >
              <Settings size={17} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className={styles.content}>
        {/* Top Command Bar */}
        <header className={styles.header}>
          <button 
            className={styles.menuButton} 
            onClick={() => setSidebarOpen(true)}
            aria-label="Open sidebar"
          >
            <Menu size={22} color="var(--text-main)" />
          </button>

          {/* Frosted Omni Search Bar */}
          <form className={styles.searchBar} onSubmit={handleSearch}>
            <Search className={styles.searchIcon} size={18} />
            <input 
              type="text" 
              placeholder="Search in AetherDrive (files, models, media)..." 
              className={styles.searchInput}
              value={searchQuery} 
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            <div className={styles.searchShortcutBadge}>
              <span>⌘</span><span>K</span>
            </div>
          </form>

          {/* Right Action Icons */}
          <div className={styles.headerActions}>
            <button 
              className={styles.iconButton} 
              title="Notifications"
            >
              <Bell size={19} />
              {notifications.length > 0 && (
                <span className={styles.notificationBadge} />
              )}
            </button>

            <button 
              className={styles.iconButton}
              onClick={() => navigate('/settings')}
              title="Security Settings"
            >
              <Settings size={19} />
            </button>
            
            <button 
              className={styles.logoutButton} 
              onClick={logout} 
              title="Logout"
            >
              <LogOut size={16} />
              <span>Logout</span>
            </button>
          </div>
        </header>

        {/* Main Content Canvas */}
        <div className={styles.mainArea}>
          {user && user.mfaEnabled === false && !hideMfaBanner && (
            <div className={styles.securityBanner}>
              <div className={styles.securityBannerLeft}>
                <Shield size={20} color="#f59e0b" />
                <span><strong>Enhance Quantum Security:</strong> Enable Two-Factor Authentication (2FA) to protect your encrypted shards.</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <button 
                  className={styles.securityBannerAction}
                  onClick={() => navigate('/settings')}
                >
                  Setup Now
                </button>
                <button 
                  className={styles.securityBannerClose}
                  onClick={() => setHideMfaBanner(true)}
                  title="Dismiss"
                >
                  <X size={18} />
                </button>
              </div>
            </div>
          )}

          <Outlet context={{ refreshKey, triggerRefresh }} />
        </div>
      </main>
    </div>
  );
};

export default MainLayout;
