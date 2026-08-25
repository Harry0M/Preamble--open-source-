import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import AiCopilot from './components/AiCopilot';

// Page imports
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Users from './pages/Users';
import UserDetail from './pages/UserDetail';
import Groups from './pages/Groups';
import Broadcasts from './pages/Broadcasts';
import Notifications from './pages/Notifications';
import Reports from './pages/Reports';
import PmMessages from './pages/PmMessages';
import PosthogAnalytics from './pages/PosthogAnalytics';
import AiConfig from './pages/AiConfig';

export default function App() {
  const [user, setUser] = useState(null);
  const [checkingAuth, setCheckingAuth] = useState(true);

  useEffect(() => {
    checkCurrentSession();
  }, []);

  const checkCurrentSession = async () => {
    try {
      const res = await fetch('/api/auth/me');
      if (res.ok) {
        const data = await res.json();
        setUser(data.user);
      } else {
        setUser(null);
      }
    } catch (e) {
      setUser(null);
    } finally {
      setCheckingAuth(false);
    }
  };

  const handleLogout = async () => {
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch (e) { /* ignore */ }
    setUser(null);
  };

  if (checkingAuth) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-dark-950">
        <div className="w-10 h-10 border-4 border-accent-orange border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  return (
    <BrowserRouter>
      {!user ? (
        <Routes>
          <Route path="/" element={<Login onLoginSuccess={setUser} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      ) : (
        <div className="min-h-screen bg-dark-950 text-dark-300 flex">
          {/* Left Navigation Sidebar */}
          <Sidebar user={user} onLogout={handleLogout} />

          {/* Main App Content Container */}
          <div className="flex-1 ml-64 min-h-screen flex flex-col justify-between">
            <main className="p-8">
              <Routes>
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/users" element={<Users />} />
                <Route path="/users/:uid" element={<UserDetail />} />
                <Route path="/groups" element={<Groups />} />
                <Route path="/broadcasts" element={<Broadcasts />} />
                <Route path="/notifications" element={<Notifications />} />
                <Route path="/reports" element={<Reports />} />
                <Route path="/pm-messages" element={<PmMessages />} />
                <Route path="/posthog-analytics" element={<PosthogAnalytics />} />
                <Route path="/ai-config" element={<AiConfig />} />
                <Route path="*" element={<Navigate to="/dashboard" replace />} />
              </Routes>
            </main>

            {/* Sticky Admin footer info */}
            <footer className="p-4 border-t border-dark-800 text-[10px] text-dark-500 font-semibold tracking-wide text-center bg-dark-950/30">
              © {new Date().getFullYear()} Preamble Administration Portal · All Rights Reserved.
            </footer>
          </div>

          {/* Global Floating AI Assistant */}
          <AiCopilot />
        </div>
      )}
    </BrowserRouter>
  );
}
