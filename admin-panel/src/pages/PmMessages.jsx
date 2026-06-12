import React, { useState, useEffect } from 'react';
import { MessageSquare, Plus, Trash2, X, RefreshCw, Power } from 'lucide-react';
import Header from '../components/Header';

export default function PmMessages() {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Form State
  const [type, setType] = useState('greeting');
  const [condition, setCondition] = useState('default');
  const [headline, setHeadline] = useState('');
  const [subtitle, setSubtitle] = useState('');
  const [priority, setPriority] = useState(0);
  const [targetType, setTargetType] = useState('all');
  const [targetUidsInput, setTargetUidsInput] = useState('');

  useEffect(() => {
    fetchMessages();
  }, []);

  const fetchMessages = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/pm-messages');
      const data = await res.json();
      setMessages(data.messages || []);
    } catch (e) {
      console.error('Failed to load PM messages:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateMessage = async (e) => {
    e.preventDefault();
    if (!headline.trim()) return;

    let targetUids = null;
    if (targetType === 'user' && targetUidsInput.trim()) {
      targetUids = targetUidsInput.split(',').map(uid => uid.trim()).filter(Boolean);
    }

    try {
      const res = await fetch('/api/pm-messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type,
          condition,
          headline: headline.trim(),
          subtitle: subtitle.trim() || null,
          priority,
          targetType,
          targetUids
        })
      });
      const data = await res.json();
      if (data.success) {
        alert('Personal Mode Message created successfully!');
        resetForm();
        setShowCreateModal(false);
        fetchMessages();
      }
    } catch (err) {
      alert('Failed to save message.');
    }
  };

  const resetForm = () => {
    setType('greeting');
    setCondition('default');
    setHeadline('');
    setSubtitle('');
    setPriority(0);
    setTargetType('all');
    setTargetUidsInput('');
  };

  const handleToggleActive = async (msg) => {
    try {
      const res = await fetch(`/api/pm-messages/${msg.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active: !msg.active })
      });
      const data = await res.json();
      if (data.success) {
        fetchMessages();
      }
    } catch (err) {
      alert('Failed to update status.');
    }
  };

  const handleDeleteMessage = async (id, title) => {
    if (!window.confirm(`Delete this message override?`)) return;

    try {
      const res = await fetch(`/api/pm-messages/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        alert('Message deleted.');
        fetchMessages();
      }
    } catch (err) {
      alert('Deletion failed.');
    }
  };

  return (
    <div className="space-y-6">
      <Header title="PM Messages" subtitle="Override hardcoded in-app strings (greetings, progress indicators, empty states, easter eggs).">
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center px-4 py-2.5 bg-white hover:bg-dark-300 text-dark-950 font-bold rounded-lg text-xs transition-colors"
        >
          <Plus className="w-4 h-4 mr-2" />
          Create Message
        </button>
      </Header>

      {/* Messages List Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {loading ? (
          <div className="col-span-full py-12 text-center">
            <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            <span className="text-dark-500 text-xs font-semibold">Loading messages...</span>
          </div>
        ) : messages.length === 0 ? (
          <div className="col-span-full glass p-8 text-center text-dark-500 text-xs font-semibold">
            No message overrides registered yet. Tap "Create Message" to get started.
          </div>
        ) : (
          messages.map(item => (
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
                      Cond: {item.condition}
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

                <div className="p-3 bg-dark-950/45 border border-dark-800 rounded-lg space-y-1">
                  <strong className="text-white text-xs block">{item.headline}</strong>
                  {item.subtitle && <p className="text-[11px] text-dark-400 leading-normal">{item.subtitle}</p>}
                </div>

                <div className="pt-2 flex flex-wrap gap-1.5 text-[9px] font-bold font-mono">
                  <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                    Target: {item.targetType}
                  </span>
                  <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                    Priority: {item.priority}
                  </span>
                </div>
              </div>

              <div className="pt-4 border-t border-dark-800 flex items-center justify-between">
                <button
                  onClick={() => handleToggleActive(item)}
                  className={`flex items-center text-xs font-bold transition-colors ${
                    item.active ? 'text-accent-green hover:text-green-500' : 'text-dark-500 hover:text-white'
                  }`}
                >
                  <Power className="w-4 h-4 mr-1.5" />
                  {item.active ? 'Disable' : 'Enable'}
                </button>
                <button
                  onClick={() => handleDeleteMessage(item.id)}
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
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">Create Message Override</h3>
              <button onClick={() => setShowCreateModal(false)} className="text-dark-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateMessage} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Message Type</label>
                  <select
                    value={type}
                    onChange={e => setType(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2.5 rounded focus:outline-none"
                  >
                    <option value="greeting">Greeting Prompt</option>
                    <option value="smart_progress">Smart Progress Summary</option>
                    <option value="empty_state">Empty State Tip</option>
                    <option value="last_task">Last Task Motivation</option>
                    <option value="streak_warn">Streak Warning Alert</option>
                    <option value="easter_egg">Easter Egg</option>
                    <option value="late_night">Late Night Mode</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Trigger Condition</label>
                  <input
                    type="text"
                    required
                    value={condition}
                    onChange={e => setCondition(e.target.value)}
                    placeholder="e.g. morning, progress_50, default"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Headline Title</label>
                <input
                  type="text"
                  required
                  value={headline}
                  onChange={e => setHeadline(e.target.value)}
                  placeholder="e.g. Good morning, productivity champion!"
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Subtitle Description (Optional)</label>
                <input
                  type="text"
                  value={subtitle}
                  onChange={e => setSubtitle(e.target.value)}
                  placeholder="Enter secondary message context..."
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4 border border-dark-800 bg-dark-900/10 p-4 rounded-xl">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Target Segment</label>
                  <select
                    value={targetType}
                    onChange={e => setTargetType(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2.5 rounded focus:outline-none"
                  >
                    <option value="all">Broadcast to All Users</option>
                    <option value="user">Specific UIDs (Comma separated)</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Priority Code</label>
                  <input
                    type="number"
                    value={priority}
                    onChange={e => setPriority(parseInt(e.target.value))}
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none"
                  />
                </div>

                {targetType === 'user' && (
                  <div className="col-span-2 space-y-1.5 animate-fade-in">
                    <label className="text-dark-500 font-bold">Comma Separated Target UIDs</label>
                    <textarea
                      value={targetUidsInput}
                      onChange={e => setTargetUidsInput(e.target.value)}
                      required
                      placeholder="uid1, uid2, uid3..."
                      className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                      rows="2"
                    />
                  </div>
                )}
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
                  Publish Message
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
