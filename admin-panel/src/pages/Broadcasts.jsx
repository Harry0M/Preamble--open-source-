import React, { useState, useEffect } from 'react';
import { Megaphone, Plus, Trash2, X, RefreshCw, Eye, Power } from 'lucide-react';
import Header from '../components/Header';

export default function Broadcasts() {
  const [broadcasts, setBroadcasts] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Form State
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [deepLink, setDeepLink] = useState('');
  const [actionUrl, setActionUrl] = useState('');
  const [actionLabel, setActionLabel] = useState('Open Now');
  const [type, setType] = useState('announcement');
  const [priority, setPriority] = useState(0);
  const [expiresAt, setExpiresAt] = useState('');
  const [targetType, setTargetType] = useState('all');
  const [targetGroupId, setTargetGroupId] = useState('');
  const [targetUidInput, setTargetUidInput] = useState('');
  const [autoNotify, setAutoNotify] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const bRes = await fetch('/api/broadcasts');
      const bData = await bRes.json();
      setBroadcasts(bData.broadcasts || []);

      const gRes = await fetch('/api/groups');
      const gData = await gRes.json();
      setGroups(gData.groups || []);
    } catch (e) {
      console.error('Failed to load data:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateBroadcast = async (e) => {
    e.preventDefault();
    if (!title.trim()) return;

    const expiryMs = expiresAt ? new Date(expiresAt).getTime() : null;
    let targetUids = null;
    if (targetType === 'single' && targetUidInput.trim()) {
      targetUids = [targetUidInput.trim()];
    }

    try {
      const res = await fetch('/api/broadcasts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: title.trim(),
          description: description.trim() || null,
          deepLink: deepLink.trim() || null,
          actionUrl: actionUrl.trim() || null,
          actionLabel,
          type,
          priority,
          expiresAt: expiryMs,
          targetType,
          targetGroupId: targetType === 'group' ? targetGroupId : null,
          targetUids,
          autoNotify
        })
      });
      const data = await res.json();
      if (data.success) {
        alert('Announcement created successfully!');
        resetForm();
        setShowCreateModal(false);
        fetchData();
      }
    } catch (err) {
      alert('Failed to save announcement.');
    }
  };

  const resetForm = () => {
    setTitle('');
    setDescription('');
    setDeepLink('');
    setActionUrl('');
    setActionLabel('Open Now');
    setType('announcement');
    setPriority(0);
    setExpiresAt('');
    setTargetType('all');
    setTargetGroupId('');
    setTargetUidInput('');
    setAutoNotify(false);
  };

  const handleToggleActive = async (id) => {
    try {
      const res = await fetch(`/api/broadcasts/${id}/toggle`, { method: 'POST' });
      const data = await res.json();
      if (data.success) {
        fetchData();
      }
    } catch (err) {
      alert('Failed to toggle status.');
    }
  };

  const handleDeleteBroadcast = async (id, title) => {
    if (!window.confirm(`Delete the announcement "${title}"?`)) return;

    try {
      const res = await fetch(`/api/broadcasts/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        alert('Announcement deleted.');
        fetchData();
      }
    } catch (err) {
      alert('Deletion failed.');
    }
  };

  return (
    <div className="space-y-6">
      <Header title="Broadcasts" subtitle="Create rich announcement cards displayed directly in-app.">
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center px-4 py-2.5 bg-white hover:bg-dark-300 text-dark-950 font-bold rounded-lg text-xs transition-colors"
        >
          <Plus className="w-4 h-4 mr-2" />
          New Announcement
        </button>
      </Header>

      {/* Broadcasts Cards Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {loading ? (
          <div className="col-span-full py-12 text-center">
            <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            <span className="text-dark-500 text-xs font-semibold">Loading broadcasts database...</span>
          </div>
        ) : broadcasts.length === 0 ? (
          <div className="col-span-full glass p-8 text-center text-dark-500 text-xs font-semibold">
            No active announcements found. Tap "New Announcement" to publish one.
          </div>
        ) : (
          broadcasts.map(item => (
            <div key={item.id} className={`glass p-5 rounded-xl border flex flex-col justify-between space-y-4 shadow-lg ${
              item.active ? 'border-accent-green/20' : 'border-dark-800'
            }`}>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-1.5">
                    <span className="px-1.5 py-0.5 bg-accent-blue/15 border border-accent-blue/20 text-accent-blue rounded text-[9px] uppercase font-bold tracking-wider">
                      {item.type}
                    </span>
                    <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded text-[9px] uppercase font-bold tracking-wider">
                      Target: {item.targetType}
                    </span>
                  </div>
                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                    item.active 
                      ? 'bg-green-500/10 text-green-400 border border-green-500/20' 
                      : 'bg-dark-800 text-dark-500 border border-dark-700'
                  }`}>
                    {item.active ? 'Active' : 'Inactive'}
                  </span>
                </div>

                <h4 className="text-sm font-bold text-white tracking-wide">{item.title}</h4>
                {item.description && (
                  <p className="text-xs text-dark-400 leading-relaxed max-w-[90%]">{item.description}</p>
                )}

                {/* Direct Action Badge Links */}
                <div className="pt-2 flex flex-wrap gap-1.5 text-[9px] font-bold font-mono">
                  {item.deepLink && (
                    <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                      DeepLink: {item.deepLink}
                    </span>
                  )}
                  {item.actionUrl && (
                    <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                      ActionURL: {item.actionUrl}
                    </span>
                  )}
                  <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                    Priority: {item.priority}
                  </span>
                </div>
              </div>

              <div className="pt-4 border-t border-dark-800 flex items-center justify-between">
                <button
                  onClick={() => handleToggleActive(item.id)}
                  className={`flex items-center text-xs font-bold transition-colors ${
                    item.active ? 'text-accent-green hover:text-green-500' : 'text-dark-500 hover:text-white'
                  }`}
                >
                  <Power className="w-4 h-4 mr-1.5" />
                  {item.active ? 'Disable' : 'Enable'}
                </button>
                <button
                  onClick={() => handleDeleteBroadcast(item.id, item.title)}
                  className="p-1.5 bg-dark-900/50 hover:bg-red-500/10 hover:text-red-400 border border-dark-800 text-dark-500 rounded transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {/* CREATE MODAL */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-dark-950/75 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="glass w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
            <div className="p-4 border-b border-dark-800 bg-dark-900/40 flex items-center justify-between">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">Publish Announcement</h3>
              <button onClick={() => setShowCreateModal(false)} className="text-dark-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateBroadcast} className="p-6 space-y-4 text-xs overflow-y-auto max-h-[75vh]">
              {/* Title & description */}
              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Announcement Title</label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={e => setTitle(e.target.value)}
                  placeholder="e.g. Server Maintenance, Try New Habits!"
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Details Description</label>
                <textarea
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="Enter detailed message contents..."
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none"
                  rows="3"
                />
              </div>

              {/* Links */}
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">In-App Deep Link (Optional)</label>
                  <input
                    type="text"
                    value={deepLink}
                    onChange={e => setDeepLink(e.target.value)}
                    placeholder="preamble://settings/theme"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">External Web Link (Optional)</label>
                  <input
                    type="text"
                    value={actionUrl}
                    onChange={e => setActionUrl(e.target.value)}
                    placeholder="https://theblankstate.com"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                  />
                </div>
              </div>

              {/* Action config */}
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Action Button Label</label>
                  <input
                    type="text"
                    value={actionLabel}
                    onChange={e => setActionLabel(e.target.value)}
                    placeholder="Open Now"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Display Category</label>
                  <select
                    value={type}
                    onChange={e => setType(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2 rounded focus:outline-none"
                  >
                    <option value="announcement">Announcement</option>
                    <option value="feature">Feature Discovery</option>
                    <option value="promotion">Promotion/Upsell</option>
                    <option value="social">Social Links</option>
                  </select>
                </div>
              </div>

              {/* Target options */}
              <div className="grid grid-cols-2 gap-4 border border-dark-800 bg-dark-900/10 p-4 rounded-xl">
                <div className="col-span-2 space-y-1.5">
                  <label className="text-dark-500 font-bold">Target Cohort Category</label>
                  <select
                    value={targetType}
                    onChange={e => setTargetType(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2 rounded focus:outline-none"
                  >
                    <option value="all">Broadcast to All Users</option>
                    <option value="group">Target Specific Group</option>
                    <option value="single">Target Single User UID</option>
                  </select>
                </div>

                {/* Conditional group target selection */}
                {targetType === 'group' && (
                  <div className="col-span-2 space-y-1.5">
                    <label className="text-dark-500 font-bold">Select Cohort Group</label>
                    <select
                      value={targetGroupId}
                      onChange={e => setTargetGroupId(e.target.value)}
                      required
                      className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2 rounded focus:outline-none"
                    >
                      <option value="">Choose Group...</option>
                      {groups.map(g => (
                        <option key={g.id} value={g.id}>{g.name}</option>
                      ))}
                    </select>
                  </div>
                )}

                {/* Conditional single target input */}
                {targetType === 'single' && (
                  <div className="col-span-2 space-y-1.5">
                    <label className="text-dark-500 font-bold">Target User UID</label>
                    <input
                      type="text"
                      value={targetUidInput}
                      onChange={e => setTargetUidInput(e.target.value)}
                      required
                      placeholder="Paste user UID..."
                      className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                    />
                  </div>
                )}
              </div>

              {/* Priority and Auto Notification */}
              <div className="flex items-center justify-between pt-2">
                <label className="flex items-center space-x-2 select-none cursor-pointer">
                  <input
                    type="checkbox"
                    checked={autoNotify}
                    onChange={e => setAutoNotify(e.target.checked)}
                    className="w-4 h-4 bg-dark-900 border border-dark-800 text-accent-orange"
                  />
                  <span className="text-white font-bold">Auto-send Push Notification (FCM)</span>
                </label>

                <div className="flex items-center space-x-2">
                  <span className="text-dark-500 font-bold">Priority Code:</span>
                  <input
                    type="number"
                    value={priority}
                    onChange={e => setPriority(parseInt(e.target.value))}
                    className="w-16 bg-dark-900 border border-dark-800 px-2 py-1.5 rounded text-white text-center"
                  />
                </div>
              </div>

              <div className="pt-4 border-t border-dark-800 flex justify-end space-x-3">
                <button
                  type="button"
                  onClick={() => {
                    resetForm();
                    setShowCreateModal(false);
                  }}
                  className="px-4 py-2 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-white hover:bg-dark-300 text-dark-950 font-bold rounded-lg"
                >
                  Publish Card
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
