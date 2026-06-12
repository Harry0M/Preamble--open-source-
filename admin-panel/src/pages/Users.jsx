import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Eye, Filter, ArrowLeft, ArrowRight, UserCheck, UserMinus } from 'lucide-react';
import Header from '../components/Header';

export default function Users() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');
  const [filterGender, setFilterGender] = useState('all');
  const [filterVersion, setFilterVersion] = useState('all');
  
  // Pagination State
  const [limit] = useState(20);
  const [pageHistory, setPageHistory] = useState([null]); // starts with null (page 1)
  const [currentPageIndex, setCurrentPageIndex] = useState(0);
  const [nextOffsetId, setNextOffsetId] = useState(null);

  const navigate = useNavigate();

  useEffect(() => {
    fetchUsers(pageHistory[currentPageIndex]);
  }, [currentPageIndex]);

  const fetchUsers = async (startAfterId = null) => {
    setLoading(true);
    try {
      let url = `/api/users?limit=${limit}`;
      if (startAfterId) {
        url += `&startAfter=${startAfterId}`;
      }

      const res = await fetch(url);
      const data = await res.json();
      setUsers(data.users || []);
      setNextOffsetId(data.nextOffsetId || null);
    } catch (e) {
      console.error('Failed to fetch users:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleNextPage = () => {
    if (!nextOffsetId) return;
    
    // Add next offset to history if not already there
    const nextIndex = currentPageIndex + 1;
    if (pageHistory.length <= nextIndex) {
      setPageHistory(prev => [...prev, nextOffsetId]);
    }
    setCurrentPageIndex(nextIndex);
  };

  const handlePrevPage = () => {
    if (currentPageIndex === 0) return;
    setCurrentPageIndex(currentPageIndex - 1);
  };

  const formatActivity = (timestamp) => {
    if (!timestamp) return { text: 'Never', color: 'text-dark-500', isLive: false };
    const diff = Date.now() - timestamp;

    if (diff < 5 * 60 * 1000) {
      return { text: 'Active Now', color: 'text-green-400 font-bold', isLive: true };
    }
    if (diff < 24 * 60 * 60 * 1000) {
      return { text: 'Active Today', color: 'text-accent-blue', isLive: false };
    }
    if (diff < 7 * 24 * 60 * 60 * 1000) {
      const days = Math.floor(diff / (24 * 60 * 60 * 1000));
      return { text: `${days}d ago`, color: 'text-dark-400', isLive: false };
    }
    const date = new Date(timestamp);
    return { text: date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }), color: 'text-dark-400', isLive: false };
  };

  // Client-side filtering on current page
  const filteredUsers = users.filter(user => {
    const q = search.toLowerCase().trim();
    if (q && !(user.email || '').toLowerCase().includes(q) && !(user.uid || '').toLowerCase().includes(q) && !(user.displayName || '').toLowerCase().includes(q)) return false;
    
    if (filterStatus === 'active' && user.blocked) return false;
    if (filterStatus === 'blocked' && !user.blocked) return false;

    if (filterGender !== 'all' && (user.gender || 'unknown').toLowerCase() !== filterGender) return false;

    if (filterVersion === 'legacy' && user.appVersionCode >= 8) return false;
    if (filterVersion === 'modern' && user.appVersionCode < 8) return false;

    return true;
  });

  return (
    <div className="space-y-6">
      <Header title="Users" subtitle="Inspect user profiles, database sync metrics, active client versions, and credentials." />

      {/* Filters Card */}
      <div className="glass p-5 rounded-xl flex flex-wrap gap-4 items-center">
        <div className="flex-1 min-w-[240px] relative">
          <Search className="absolute left-3 top-3 w-4 h-4 text-dark-500" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search by email, name, or UID..."
            className="w-full bg-dark-900 border border-dark-800 text-xs text-white placeholder-dark-500 pl-10 pr-4 py-2.5 rounded-lg focus:outline-none focus:border-accent-orange transition-colors"
          />
        </div>

        <div className="flex gap-3 flex-wrap">
          <select
            value={filterStatus}
            onChange={e => setFilterStatus(e.target.value)}
            className="bg-dark-900 border border-dark-800 text-xs text-white px-3 py-2.5 rounded-lg focus:outline-none focus:border-accent-orange"
          >
            <option value="all">All Status</option>
            <option value="active">Active</option>
            <option value="blocked">Blocked</option>
          </select>

          <select
            value={filterGender}
            onChange={e => setFilterGender(e.target.value)}
            className="bg-dark-900 border border-dark-800 text-xs text-white px-3 py-2.5 rounded-lg focus:outline-none focus:border-accent-orange"
          >
            <option value="all">All Genders</option>
            <option value="male">Male</option>
            <option value="female">Female</option>
            <option value="other">Other</option>
          </select>

          <select
            value={filterVersion}
            onChange={e => setFilterVersion(e.target.value)}
            className="bg-dark-900 border border-dark-800 text-xs text-white px-3 py-2.5 rounded-lg focus:outline-none focus:border-accent-orange"
          >
            <option value="all">All App Versions</option>
            <option value="modern">V2 (Code 8+)</option>
            <option value="legacy">V1 (Code 1-7)</option>
          </select>
        </div>
      </div>

      {/* Users Table */}
      <div className="glass rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="bg-dark-900/60 border-b border-dark-800 text-dark-400 font-bold uppercase tracking-wider">
                <th className="p-4">Email / Name</th>
                <th className="p-4">User ID</th>
                <th className="p-4">Gender / Age</th>
                <th className="p-4">App Version</th>
                <th className="p-4">Task Count</th>
                <th className="p-4">Status</th>
                <th className="p-4">Last Sync</th>
                <th className="p-4 text-center">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-dark-800 text-dark-300">
              {loading ? (
                <tr>
                  <td colSpan="8" className="p-10 text-center">
                    <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    <span className="text-dark-500 font-medium">Loading user batch...</span>
                  </td>
                </tr>
              ) : filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan="8" className="p-10 text-center text-dark-500 font-medium">
                    No users match filters on this page.
                  </td>
                </tr>
              ) : (
                filteredUsers.map(user => {
                  const activity = formatActivity(user.lastSeenAt);
                  return (
                    <tr key={user.uid} className="hover:bg-dark-900/35 transition-colors">
                      <td className="p-4">
                        <div className="font-semibold text-white truncate max-w-[180px]">
                          {user.displayName !== 'N/A' ? user.displayName : user.email}
                        </div>
                        {user.displayName !== 'N/A' && (
                          <div className="text-[10px] text-dark-500 mt-0.5 truncate max-w-[180px]">
                            {user.email}
                          </div>
                        )}
                      </td>
                      <td className="p-4 font-mono text-[10px] text-dark-500">
                        {user.uid.substring(0, 16)}...
                      </td>
                      <td className="p-4">
                        <span className="capitalize">{user.gender || '—'}</span>
                        {user.age && <span className="text-dark-500"> · {user.age} yrs</span>}
                      </td>
                      <td className="p-4">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                          user.appVersionCode >= 8 
                            ? 'bg-accent-green/10 border-accent-green/20 text-accent-green' 
                            : 'bg-dark-800 border-dark-700 text-dark-400'
                        }`}>
                          Code {user.appVersionCode || '?' } ({user.appVersionName})
                        </span>
                      </td>
                      <td className="p-4 font-semibold text-white">{user.taskCount}</td>
                      <td className="p-4">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          user.blocked 
                            ? 'bg-red-500/10 text-red-400 border border-red-500/20' 
                            : 'bg-green-500/10 text-green-400 border border-green-500/20'
                        }`}>
                          {user.blocked ? 'Blocked' : 'Active'}
                        </span>
                      </td>
                      <td className="p-4">
                        <div className="flex items-center space-x-2">
                          {activity.isLive && (
                            <span className="w-2.5 h-2.5 rounded-full bg-green-400 pulse-dot flex-shrink-0" />
                          )}
                          <span className={activity.color}>{activity.text}</span>
                        </div>
                      </td>
                      <td className="p-4 text-center">
                        <button
                          onClick={() => navigate(`/users/${user.uid}`)}
                          className="p-1.5 bg-dark-800 hover:bg-dark-700 text-white rounded transition-colors"
                          title="View user details"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Controls Footer */}
        <div className="p-4 border-t border-dark-800 bg-dark-900/30 flex items-center justify-between text-xs">
          <span className="text-dark-500 font-semibold uppercase">
            Page {currentPageIndex + 1}
          </span>
          <div className="flex space-x-2">
            <button
              onClick={handlePrevPage}
              disabled={currentPageIndex === 0 || loading}
              className="flex items-center px-3.5 py-2 bg-dark-800 hover:bg-dark-700 disabled:opacity-40 text-white font-bold rounded-lg transition-colors"
            >
              <ArrowLeft className="w-4 h-4 mr-1.5" />
              Previous
            </button>
            <button
              onClick={handleNextPage}
              disabled={!nextOffsetId || loading}
              className="flex items-center px-3.5 py-2 bg-dark-800 hover:bg-dark-700 disabled:opacity-40 text-white font-bold rounded-lg transition-colors"
            >
              Next
              <ArrowRight className="w-4 h-4 ml-1.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
