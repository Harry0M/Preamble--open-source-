import React, { useState, useEffect } from 'react';
import { Bell, Send, RefreshCw, Sparkles, Loader2, Calendar } from 'lucide-react';
import Header from '../components/Header';

export default function Notifications() {
  const [history, setHistory] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);

  // Form State
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [deepLink, setDeepLink] = useState('');
  const [channelType, setChannelType] = useState('broadcast');
  const [targetType, setTargetType] = useState('all');
  const [targetGroupId, setTargetGroupId] = useState('');
  const [targetUid, setTargetUid] = useState('');
  const [sending, setSending] = useState(false);

  // AI Copywriter State
  const [aiContext, setAiContext] = useState('');
  const [aiGenerating, setAiGenerating] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const hRes = await fetch('/api/notifications/history');
      const hData = await hRes.json();
      setHistory(hData.history || []);

      const gRes = await fetch('/api/groups');
      const gData = await gRes.json();
      setGroups(gData.groups || []);
    } catch (e) {
      console.error('Failed to load notification logs:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleSendNotification = async (e) => {
    e.preventDefault();
    if (!title.trim() || !body.trim() || sending) return;

    // Strict UI validation check to prevent mass-spamming fallbacks on backend
    if (targetType === 'single' && !targetUid.trim()) {
      alert('Validation Error: Target UID is required for single-user delivery.');
      return;
    }
    if (targetType === 'group' && !targetGroupId) {
      alert('Validation Error: Target Cohort Group is required.');
      return;
    }

    setSending(true);
    try {
      const res = await fetch('/api/notifications/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: title.trim(),
          body: body.trim(),
          deepLink: deepLink.trim() || null,
          channelType,
          targetType,
          targetUid: targetType === 'single' ? targetUid.trim() : null,
          targetGroupId: targetType === 'group' ? targetGroupId : null
        })
      });
      const data = await res.json();
      if (data.success) {
        alert(`Success! Push notification sent to ${data.sent} device(s).`);
        setTitle('');
        setBody('');
        setDeepLink('');
        setTargetUid('');
        setTargetGroupId('');
        fetchData();
      } else {
        alert('Error: ' + data.error);
      }
    } catch (err) {
      alert('Network error trigger failed.');
    } finally {
      setSending(false);
    }
  };

  const handleGenerateAiCopy = async () => {
    if (!aiContext.trim() || aiGenerating) return;
    setAiGenerating(true);

    try {
      const res = await fetch('/api/ai/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'notification',
          context: aiContext.trim()
        })
      });
      const data = await res.json();
      if (data.success && data.generated) {
        setTitle(data.generated.title || '');
        setBody(data.generated.body || '');
        alert('AI Copywriter drafted matching notification successfully!');
      } else {
        alert('AI copywriting generation failed.');
      }
    } catch (err) {
      alert('Could not contact copywriting API.');
    } finally {
      setAiGenerating(false);
    }
  };

  return (
    <div className="space-y-6">
      <Header title="Notifications" subtitle="Dispatch high-priority push notifications (FCM) to single devices or cohorts." />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Campaign Composer Form */}
        <div className="lg:col-span-2 space-y-6">
          {/* AI Copywriter Sidebar */}
          <div className="glass p-6 rounded-xl space-y-4">
            <div className="flex items-center space-x-2 text-accent-orange">
              <Sparkles className="w-5 h-5" />
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">AI Notification Copywriter</h3>
            </div>
            <div className="flex items-center space-x-2 text-xs">
              <input
                type="text"
                value={aiContext}
                onChange={e => setAiContext(e.target.value)}
                placeholder="e.g. reminding users to complete tasks before bedtime..."
                className="flex-1 bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
              />
              <button
                onClick={handleGenerateAiCopy}
                disabled={aiGenerating || !aiContext.trim()}
                className="px-4 py-2.5 bg-accent-orange hover:bg-accent-orange/80 disabled:opacity-50 text-white font-bold rounded-lg transition-colors flex items-center"
              >
                {aiGenerating ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  'Generate'
                )}
              </button>
            </div>
          </div>

          {/* Standard Composer */}
          <div className="glass p-6 rounded-xl space-y-4">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider border-b border-dark-800 pb-3">Campaign Details</h3>
            <form onSubmit={handleSendNotification} className="space-y-4 text-xs">
              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Notification Title</label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={e => setTitle(e.target.value)}
                  placeholder="Keep it action-oriented (max 60 chars)..."
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Body Message</label>
                <textarea
                  required
                  value={body}
                  onChange={e => setBody(e.target.value)}
                  placeholder="Enter main push body message (max 160 chars)..."
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
                  rows="3"
                />
              </div>

              {/* Targeting Segment Rules */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border border-dark-800 bg-dark-900/10 p-4 rounded-xl">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Select Target Type</label>
                  <select
                    value={targetType}
                    onChange={e => setTargetType(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2.5 rounded focus:outline-none"
                  >
                    <option value="all">Broadcast to All Users</option>
                    <option value="group">Target Cohort Group</option>
                    <option value="single">Target Specific UID</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Optional Deep Link</label>
                  <input
                    type="text"
                    value={deepLink}
                    onChange={e => setDeepLink(e.target.value)}
                    placeholder="preamble://home"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
                  />
                </div>

                {/* Target details conditional */}
                {targetType === 'group' && (
                  <div className="col-span-2 space-y-1.5 animate-fade-in">
                    <label className="text-dark-500 font-bold">Select Target Cohort Group</label>
                    <select
                      value={targetGroupId}
                      onChange={e => setTargetGroupId(e.target.value)}
                      required
                      className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2 rounded focus:outline-none"
                    >
                      <option value="">Choose Cohort...</option>
                      {groups.map(g => (
                        <option key={g.id} value={g.id}>{g.name}</option>
                      ))}
                    </select>
                  </div>
                )}

                {targetType === 'single' && (
                  <div className="col-span-2 space-y-1.5 animate-fade-in">
                    <label className="text-dark-500 font-bold">Target User UID</label>
                    <input
                      type="text"
                      value={targetUid}
                      onChange={e => setTargetUid(e.target.value)}
                      required
                      placeholder="Paste target UID..."
                      className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none focus:border-accent-orange"
                    />
                  </div>
                )}
              </div>

              <div className="pt-2 border-t border-dark-800 flex justify-end">
                <button
                  type="submit"
                  disabled={sending}
                  className="px-6 py-2.5 bg-white hover:bg-dark-300 text-dark-955 font-bold rounded-lg flex items-center transition-colors"
                >
                  {sending ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      Sending notification...
                    </>
                  ) : (
                    <>
                      <Send className="w-4 h-4 mr-2" />
                      Dispatch Notification
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>

        {/* Campaign History Log */}
        <div className="lg:col-span-1 glass p-6 rounded-xl flex flex-col h-[580px] overflow-hidden">
          <div className="border-b border-dark-800 pb-3 flex justify-between items-center">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider">Sent Campaigns (Last 50)</h3>
            <button onClick={fetchData} className="p-1 hover:bg-dark-800 rounded transition-colors text-dark-400 hover:text-white">
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto space-y-4 pt-4 text-xs pr-1">
            {loading ? (
              <div className="py-8 text-center flex flex-col items-center justify-center">
                <Loader2 className="w-6 h-6 text-accent-orange animate-spin mb-2" />
                <span className="text-dark-500 font-medium">Loading logs...</span>
              </div>
            ) : history.length === 0 ? (
              <div className="py-8 text-center text-dark-500 font-medium">
                No campaign logs recorded yet.
              </div>
            ) : (
              history.map(item => (
                <div key={item.id} className="p-3 bg-dark-900 border border-dark-800 rounded-lg space-y-2">
                  <div className="flex items-center justify-between text-[10px] text-dark-500">
                    <span>Target: <strong className="text-dark-400 capitalize">{item.targetType}</strong></span>
                    <span>{new Date(item.sentAt).toLocaleDateString()}</span>
                  </div>
                  <h4 className="font-bold text-white truncate">{item.title}</h4>
                  <p className="text-dark-400 leading-normal line-clamp-2 text-[11px]">{item.body}</p>
                  <div className="pt-2 border-t border-dark-800 flex items-center justify-between text-[10px]">
                    <span className="text-dark-500">Sent By: <strong className="text-dark-400">{item.sentBy?.split('@')[0]}</strong></span>
                    <span className="px-2 py-0.5 bg-accent-green/10 border border-accent-green/20 text-accent-green rounded font-bold">
                      {item.sentCount} sent
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
