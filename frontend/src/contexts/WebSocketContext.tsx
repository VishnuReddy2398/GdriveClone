import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from './AuthContext';

interface WebSocketContextType {
  connected: boolean;
  notifications: any[];
  clearNotifications: () => void;
}

const WebSocketContext = createContext<WebSocketContextType>({
  connected: false,
  notifications: [],
  clearNotifications: () => {},
});

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [connected, setConnected] = useState(false);
  const [notifications, setNotifications] = useState<any[]>([]);
  const clientRef = useRef<Client | null>(null);
  useEffect(() => {
    if (!user) return;

    const token = localStorage.getItem('token');
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        setConnected(true);
        console.log('[WebSocket] Connected');

        // Subscribe to the user's notification topic
        // Note: userId is stored as 'unknown' in our current auth flow.
        // In production, the backend JWT would contain the real userId.
        client.subscribe(`/topic/user.${user.id}.notifications`, (message) => {
          const body = JSON.parse(message.body);
          console.log('[WebSocket] Notification received:', body);
          setNotifications(prev => [body, ...prev]);
        });

        client.subscribe(`/topic/user.${user.id}.drive`, (message) => {
          const body = JSON.parse(message.body);
          console.log('[WebSocket] Drive update received:', body);
          // Could trigger a refetch of the file list here
        });
      },
      onDisconnect: () => {
        setConnected(false);
        console.log('[WebSocket] Disconnected');
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP error:', frame.headers['message']);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [user]);

  const clearNotifications = useCallback(() => {
    setNotifications([]);
  }, []);

  return (
    <WebSocketContext.Provider value={{ connected, notifications, clearNotifications }}>
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocket = () => useContext(WebSocketContext);
