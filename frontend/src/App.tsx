import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { WebSocketProvider } from './contexts/WebSocketContext';
import { ToastProvider } from './contexts/ToastContext';
import { UploadProvider } from './contexts/UploadContext';
import UploadTray from './components/UploadTray';
import Login from './pages/Login';
import Register from './pages/Register';
import PublicShare from './pages/PublicShare';
import MainLayout from './components/MainLayout';
import Drive from './pages/Drive';
import Trash from './pages/Trash';
import Settings from './pages/Settings';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, loading } = useAuth();

  if (loading) return <div>Loading...</div>;
  if (!user) return <Navigate to="/login" replace />;

  return <>{children}</>;
};

function App() {
  return (
    <AuthProvider>
      <WebSocketProvider>
        <ToastProvider>
          <UploadProvider>
            <Router>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/share/:token" element={<PublicShare />} />
              
              <Route path="/" element={
                <ProtectedRoute>
                  <MainLayout />
                </ProtectedRoute>
              }>
                <Route index element={<Navigate to="/drive/my-drive" replace />} />
                <Route path="drive/my-drive" element={<Drive />} />
                <Route path="drive/folders/:folderId" element={<Drive />} />
                <Route path="drive/recent" element={<Drive filter="recent" />} />
                <Route path="drive/starred" element={<Drive filter="starred" />} />
                <Route path="drive/shared" element={<Drive filter="shared" />} />
                <Route path="drive/trash" element={<Trash />} />
                <Route path="settings" element={<Settings />} />
              </Route>
            </Routes>
            </Router>
            <UploadTray />
          </UploadProvider>
        </ToastProvider>
      </WebSocketProvider>
    </AuthProvider>
  );
}

export default App;
