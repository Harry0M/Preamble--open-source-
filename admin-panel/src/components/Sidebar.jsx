import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Users, 
  UserSquare2, 
  Megaphone, 
  Bell, 
  FileText, 
  MessageSquare, 
  LogOut,
  LineChart
} from 'lucide-react';

export default function Sidebar({ user, onLogout }) {
  const navigate = useNavigate();

  const menuItems = [
    { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    { name: 'Users', path: '/users', icon: Users },
    { name: 'Groups', path: '/groups', icon: UserSquare2 },
    { name: 'Broadcasts', path: '/broadcasts', icon: Megaphone },
    { name: 'Notifications', path: '/notifications', icon: Bell },
    { name: 'Reports', path: '/reports', icon: FileText },
    { name: 'PM Messages', path: '/pm-messages', icon: MessageSquare },
    { name: 'Telemetry', path: '/posthog-analytics', icon: LineChart },
  ];

  return (
    <aside className="w-64 bg-dark-900 border-r border-dark-800 flex flex-col h-screen fixed left-0 top-0 z-30">
      {/* Brand Header */}
      <div className="p-6 border-b border-dark-800">
        <h1 className="text-xl font-bold font-heading text-white tracking-wide">Preamble</h1>
        <span className="text-xs text-dark-400 font-semibold tracking-widest uppercase">Admin Panel</span>
      </div>

      {/* Navigation Menu */}
      <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
        {menuItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.name}
              to={item.path}
              className={({ isActive }) =>
                `flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-all duration-200 group ${
                  isActive
                    ? 'bg-white text-dark-950 font-bold shadow-md shadow-white/5'
                    : 'text-dark-400 hover:text-white hover:bg-dark-800'
                }`
              }
            >
              <Icon className="w-5 h-5 mr-3 flex-shrink-0 group-hover:scale-105 transition-transform" />
              {item.name}
            </NavLink>
          );
        })}
      </nav>

      {/* Admin Profile & Sign Out Footer */}
      <div className="p-4 border-t border-dark-800 bg-dark-950/40">
        <div className="flex items-center space-x-3 mb-4">
          <div className="w-10 h-10 rounded-full bg-dark-800 border border-dark-700 overflow-hidden flex items-center justify-center flex-shrink-0">
            {user?.picture ? (
              <img src={user.picture} alt="avatar" className="w-full h-full object-cover" />
            ) : (
              <span className="text-sm font-bold text-white uppercase">
                {(user?.email || '?')[0]}
              </span>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <h4 className="text-xs font-semibold text-white truncate">{user?.name || 'Admin'}</h4>
            <p className="text-[10px] text-dark-400 truncate">{user?.email}</p>
            <span className="inline-block px-1.5 py-0.5 mt-1 text-[8px] font-bold text-accent-orange bg-accent-orange/10 border border-accent-orange/20 rounded uppercase">
              {user?.role?.replace('_', ' ')}
            </span>
          </div>
        </div>
        <button
          onClick={onLogout}
          className="w-full flex items-center justify-center px-4 py-2.5 text-xs font-bold text-red-400 hover:text-red-300 hover:bg-red-500/10 border border-red-500/20 rounded-lg transition-all duration-200"
        >
          <LogOut className="w-4 h-4 mr-2" />
          Sign Out
        </button>
      </div>
    </aside>
  );
}
