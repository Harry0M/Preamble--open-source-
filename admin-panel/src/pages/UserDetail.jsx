import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  ArrowLeft, 
  User, 
  CheckSquare, 
  CreditCard, 
  ShieldAlert, 
  Calendar, 
  Info,
  CheckCircle2,
  XCircle,
  Plus
} from 'lucide-react';
import Header from '../components/Header';

const VALID_TIERS = ['FREE_TIER', 'UNPREMIUM', 'PROMOTIONAL', 'PREMIUM', 'PREMIUM_STUDENT', 'PREMIUM_YOUNGSTER'];

export default function UserDetail() {
  const { uid } = useParams();
  const navigate = useNavigate();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('profile');
  const [savingEntitlement, setSavingEntitlement] = useState(false);
  const [savingBlock, setSavingBlock] = useState(false);

  // Entitlement Inputs State
  const [tier, setTier] = useState('FREE_TIER');
  const [expiresAt, setExpiresAt] = useState('');
  const [studentExpiresAt, setStudentExpiresAt] = useState('');
  const [youngsterExpiresAt, setYoungsterExpiresAt] = useState('');

  // New task builder state (optional testing helper)
  const [taskTitle, setTaskTitle] = useState('');
  const [taskIsHabit, setTaskIsHabit] = useState(false);
  const [taskIsEvent, setTaskIsEvent] = useState(false);
  const [taskPriority, setTaskPriority] = useState(0);

  useEffect(() => {
    fetchUserDetail();
  }, [uid]);

  const fetchUserDetail = async () => {
    setLoading(true);
    try {
      const res = await fetch(`/api/users/${uid}`);
      const result = await res.json();
      setData(result);

      // Pre-fill entitlement form
      if (result.user) {
        setTier(result.user.entitlement_tier || 'FREE_TIER');
        setExpiresAt(msToDateInput(result.user.entitlement_expires_at));
        setStudentExpiresAt(msToDateInput(result.user.entitlement_student_expires_at));
        setYoungsterExpiresAt(msToDateInput(result.user.entitlement_youngster_expires_at));
      }
    } catch (e) {
      console.error('Failed to load user details:', e);
    } finally {
      setLoading(false);
    }
  };

  const msToDateInput = (ms) => {
    if (!ms || ms === 0) return '';
    const d = new Date(ms);
    return d.toISOString().split('T')[0];
  };

  const dateInputToMs = (dateStr) => {
    if (!dateStr) return 0;
    return new Date(dateStr).getTime();
  };

  const saveEntitlement = async () => {
    setSavingEntitlement(true);
    try {
      const res = await fetch(`/api/users/${uid}/entitlement`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tier,
          expiresAtMs: dateInputToMs(expiresAt),
          studentValidityExpiresAtMs: dateInputToMs(studentExpiresAt),
          youngsterValidityExpiresAtMs: dateInputToMs(youngsterExpiresAt)
        })
      });
      const result = await res.json();
      if (result.success) {
        alert('Entitlements updated successfully!');
        fetchUserDetail();
      } else {
        alert('Error: ' + result.error);
      }
    } catch (e) {
      alert('Save failed.');
    } finally {
      setSavingEntitlement(false);
    }
  };

  const toggleBlock = async () => {
    if (!window.confirm(`Are you sure you want to ${data.user.blocked ? 'UNBLOCK' : 'BLOCK'} this user?`)) return;
    
    setSavingBlock(true);
    try {
      const res = await fetch(`/api/users/${uid}/block`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ blocked: !data.user.blocked })
      });
      const result = await res.json();
      if (result.success) {
        alert(`User successfully ${!data.user.blocked ? 'blocked' : 'unblocked'}!`);
        fetchUserDetail();
      }
    } catch (e) {
      alert('Action failed.');
    } finally {
      setSavingBlock(false);
    }
  };

  const handleCreateTask = async (e) => {
    e.preventDefault();
    if (!taskTitle.trim()) return;

    try {
      const res = await fetch(`/api/users/${uid}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: taskTitle.trim(),
          isHabit: taskIsHabit,
          isEvent: taskIsEvent,
          priority: taskPriority
        })
      });
      const result = await res.json();
      if (res.ok) {
        alert('Task created successfully!');
        setTaskTitle('');
        setTaskIsHabit(false);
        setTaskIsEvent(false);
        setTaskPriority(0);
        fetchUserDetail();
      } else {
        alert(result.error);
      }
    } catch (err) {
      alert('Failed to save task.');
    }
  };

  const fillValidityPreset = (tierVal, days) => {
    setTier(tierVal);
    const futureDate = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    setExpiresAt(futureDate);
    if (tierVal === 'PREMIUM_STUDENT') {
      setStudentExpiresAt(futureDate);
    } else if (tierVal === 'PREMIUM_YOUNGSTER') {
      setYoungsterExpiresAt(futureDate);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-10 h-10 border-4 border-accent-orange border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  const user = data.user;
  const isLegacyUser = user.appVersionCode < 8;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center space-x-3 mb-2">
        <button
          onClick={() => navigate('/users')}
          className="p-2 bg-dark-900 border border-dark-800 hover:bg-dark-800 text-white rounded-lg transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <Header title={user.email} subtitle={`UID: ${user.uid}`} />
      </div>

      {/* Version Warning Banner */}
      {isLegacyUser && (
        <div className="p-4 bg-accent-orange/10 border border-accent-orange/20 rounded-xl text-xs text-accent-orange flex items-start space-x-3">
          <Info className="w-5 h-5 flex-shrink-0" />
          <div>
            <p className="font-bold">Legacy Client Compatibility Notice</p>
            <p className="mt-1 opacity-90">
              This user is running app version code <strong>{user.appVersionCode} ({user.appVersionName})</strong>. 
              Because this version is less than 8, it does not support habits, events, or custom recurrence schedules. 
              The task editor is locked for these features to prevent client database sync crashes.
            </p>
          </div>
        </div>
      )}

      {/* Profile Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Navigation Tabs */}
        <div className="lg:col-span-1 flex flex-col space-y-2">
          {[
            { id: 'profile', label: 'User Profile', icon: User },
            { id: 'tasks', label: 'Tasks Explorer', icon: CheckSquare },
            { id: 'entitlements', label: 'Entitlements', icon: CreditCard },
            { id: 'security', label: 'Security & Access', icon: ShieldAlert }
          ].map(tab => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center px-4 py-3 text-xs font-bold uppercase tracking-wider rounded-lg border text-left transition-all ${
                  activeTab === tab.id
                    ? 'bg-white border-white text-dark-950 shadow'
                    : 'bg-dark-900/40 border-dark-800 text-dark-400 hover:bg-dark-800 hover:text-white'
                }`}
              >
                <Icon className="w-4 h-4 mr-3" />
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Tab Content Window */}
        <div className="lg:col-span-3">
          {/* PROFILE TAB */}
          {activeTab === 'profile' && (
            <div className="glass p-6 rounded-xl space-y-6">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider border-b border-dark-800 pb-3">User Profile Metadata</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-xs">
                <div>
                  <span className="text-dark-500 font-bold block mb-1">Display Name</span>
                  <div className="bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white font-medium">{user.displayName}</div>
                </div>
                <div>
                  <span className="text-dark-500 font-bold block mb-1">Email Address</span>
                  <div className="bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white font-medium">{user.email}</div>
                </div>
                <div>
                  <span className="text-dark-500 font-bold block mb-1">Gender / Age</span>
                  <div className="bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white font-medium capitalize">
                    {user.gender || 'Not Specified'} {user.age ? ` · ${user.age} yrs` : ''}
                  </div>
                </div>
                <div>
                  <span className="text-dark-500 font-bold block mb-1">App Version</span>
                  <div className="bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white font-medium">
                    Code {user.appVersionCode || 'Unknown'} ({user.appVersionName})
                  </div>
                </div>
                <div>
                  <span className="text-dark-500 font-bold block mb-1">Account Creation Date</span>
                  <div className="bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white font-medium">
                    {user.creationTime ? new Date(user.creationTime).toLocaleString() : 'N/A'}
                  </div>
                </div>
                <div>
                  <span className="text-dark-500 font-bold block mb-1">Last Authentication Sign-In</span>
                  <div className="bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white font-medium">
                    {user.lastSignInTime ? new Date(user.lastSignInTime).toLocaleString() : 'N/A'}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TASKS TAB */}
          {activeTab === 'tasks' && (
            <div className="space-y-6">
              {/* Task Creation Sandbox */}
              <div className="glass p-6 rounded-xl space-y-4">
                <h3 className="text-sm font-bold text-white uppercase tracking-wider border-b border-dark-800 pb-3">Trigger Task Sandbox</h3>
                <form onSubmit={handleCreateTask} className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end text-xs">
                  <div className="md:col-span-2 space-y-2">
                    <label className="text-dark-500 font-bold">Task Title</label>
                    <input
                      type="text"
                      value={taskTitle}
                      onChange={e => setTaskTitle(e.target.value)}
                      placeholder="Enter temporary task title..."
                      className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
                    />
                  </div>

                  <button
                    type="submit"
                    className="w-full bg-white hover:bg-dark-300 text-dark-950 font-bold py-2.5 rounded-lg flex items-center justify-center transition-colors"
                  >
                    <Plus className="w-4 h-4 mr-2" />
                    Create Task
                  </button>

                  <div className="md:col-span-3 flex items-center space-x-6 mt-2">
                    <label className={`flex items-center space-x-2 select-none ${isLegacyUser ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}`}>
                      <input
                        type="checkbox"
                        checked={taskIsHabit}
                        disabled={isLegacyUser}
                        onChange={e => setTaskIsHabit(e.target.checked)}
                        className="w-4 h-4 bg-dark-900 border border-dark-800 text-accent-orange"
                      />
                      <span className="text-white font-bold">Track as Habit</span>
                    </label>

                    <label className={`flex items-center space-x-2 select-none ${isLegacyUser ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}`}>
                      <input
                        type="checkbox"
                        checked={taskIsEvent}
                        disabled={isLegacyUser}
                        onChange={e => setTaskIsEvent(e.target.checked)}
                        className="w-4 h-4 bg-dark-900 border border-dark-800 text-accent-orange"
                      />
                      <span className="text-white font-bold">Mark as Event</span>
                    </label>

                    <div className="flex items-center space-x-2">
                      <span className="text-dark-500 font-bold">Priority:</span>
                      <select
                        value={taskPriority}
                        onChange={e => setTaskPriority(parseInt(e.target.value))}
                        className="bg-dark-900 border border-dark-800 px-2 py-1.5 rounded text-white"
                      >
                        <option value="0">Normal</option>
                        <option value="1">Medium</option>
                        <option value="2">High</option>
                      </select>
                    </div>
                  </div>
                </form>
              </div>

              {/* Tasks Explorer Table */}
              <div className="glass rounded-xl overflow-hidden">
                <div className="p-4 border-b border-dark-800 bg-dark-900/30">
                  <h3 className="text-sm font-bold text-white uppercase tracking-wider">Recent Tasks (Max 100)</h3>
                </div>
                <div className="overflow-x-auto text-xs">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-dark-900/30 border-b border-dark-800 text-dark-500 font-bold uppercase">
                        <th className="p-4">Title</th>
                        <th className="p-4">Creation Date</th>
                        <th className="p-4">Priority</th>
                        <th className="p-4">Type / Settings</th>
                        <th className="p-4 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-dark-800 text-dark-300">
                      {data.tasks?.length === 0 ? (
                        <tr>
                          <td colSpan="5" className="p-8 text-center text-dark-500 font-medium">
                            No synced tasks registered for this account.
                          </td>
                        </tr>
                      ) : (
                        data.tasks?.map(task => (
                          <tr key={task.docId} className="hover:bg-dark-900/20 transition-colors">
                            <td className="p-4 font-semibold text-white truncate max-w-[240px]">
                              {task.title}
                            </td>
                            <td className="p-4">{task.createdDate}</td>
                            <td className="p-4">
                              <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                task.priority === 2 
                                  ? 'bg-red-500/10 text-red-400 border border-red-500/20' 
                                  : task.priority === 1 
                                  ? 'bg-accent-orange/10 text-accent-orange border border-accent-orange/20'
                                  : 'bg-dark-800 border-dark-700 text-dark-400'
                              }`}>
                                {task.priority === 2 ? 'High' : task.priority === 1 ? 'Medium' : 'Normal'}
                              </span>
                            </td>
                            <td className="p-4">
                              <div className="flex flex-wrap gap-1">
                                {task.recurrenceType && (
                                  <span className="px-1.5 py-0.5 bg-accent-blue/10 border border-accent-blue/20 text-accent-blue rounded text-[10px] uppercase font-bold">
                                    {task.recurrenceType}
                                  </span>
                                )}
                                {task.isHabit && (
                                  <span className="px-1.5 py-0.5 bg-accent-orange/10 border border-accent-orange/20 text-accent-orange rounded text-[10px] uppercase font-bold">
                                    Habit
                                  </span>
                                )}
                                {task.isEvent && (
                                  <span className="px-1.5 py-0.5 bg-accent-green/10 border border-accent-green/20 text-accent-green rounded text-[10px] uppercase font-bold">
                                    Event
                                  </span>
                                )}
                              </div>
                            </td>
                            <td className="p-4 text-center">
                              {task.isCompleted ? (
                                <CheckCircle2 className="w-5 h-5 text-accent-green mx-auto" />
                              ) : (
                                <XCircle className="w-5 h-5 text-dark-500 mx-auto" />
                              )}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* ENTITLEMENTS TAB */}
          {activeTab === 'entitlements' && (
            <div className="glass p-6 rounded-xl space-y-6">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider border-b border-dark-800 pb-3">Entitlement Editor (Server-side Source of Truth)</h3>
              
              {/* Presets Grid */}
              <div className="space-y-2">
                <span className="text-xs font-bold text-dark-500 block uppercase">Assign Validity Presets</span>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <button
                    onClick={() => fillValidityPreset('PROMOTIONAL', 9999)}
                    className="px-3 py-2 bg-dark-900 border border-dark-800 hover:bg-dark-800 text-white rounded text-xs font-bold transition-all text-center"
                  >
                    Preset: Promo (Lifetime)
                  </button>
                  <button
                    onClick={() => fillValidityPreset('PREMIUM', 365)}
                    className="px-3 py-2 bg-dark-900 border border-dark-800 hover:bg-dark-800 text-white rounded text-xs font-bold transition-all text-center"
                  >
                    Preset: Premium +1 Year
                  </button>
                  <button
                    onClick={() => fillValidityPreset('PREMIUM_STUDENT', 365)}
                    className="px-3 py-2 bg-dark-900 border border-dark-800 hover:bg-dark-800 text-white rounded text-xs font-bold transition-all text-center"
                  >
                    Preset: Student +1 Year
                  </button>
                </div>
              </div>

              {/* Form Input Fields */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-xs">
                <div className="space-y-2">
                  <label className="text-dark-500 font-bold">Select Subscription Tier</label>
                  <select
                    value={tier}
                    onChange={e => setTier(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2.5 rounded focus:outline-none"
                  >
                    {VALID_TIERS.map(t => (
                      <option key={t} value={t}>{t.replace('_', ' ')}</option>
                    ))}
                  </select>
                </div>

                <div className="space-y-2">
                  <label className="text-dark-500 font-bold">General Expiry Date</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-3 w-4 h-4 text-dark-500" />
                    <input
                      type="date"
                      value={expiresAt}
                      onChange={e => setExpiresAt(e.target.value)}
                      className="w-full bg-dark-900 border border-dark-800 text-white pl-10 pr-4 py-2.5 rounded focus:outline-none"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-dark-500 font-bold">Student Validity Expiry Date</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-3 w-4 h-4 text-dark-500" />
                    <input
                      type="date"
                      value={studentExpiresAt}
                      onChange={e => setStudentExpiresAt(e.target.value)}
                      className="w-full bg-dark-900 border border-dark-800 text-white pl-10 pr-4 py-2.5 rounded focus:outline-none"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-dark-500 font-bold">Youngster Validity Expiry Date</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-3 w-4 h-4 text-dark-500" />
                    <input
                      type="date"
                      value={youngsterExpiresAt}
                      onChange={e => setYoungsterExpiresAt(e.target.value)}
                      className="w-full bg-dark-900 border border-dark-800 text-white pl-10 pr-4 py-2.5 rounded focus:outline-none"
                    />
                  </div>
                </div>
              </div>

              <div className="pt-4 border-t border-dark-800 flex justify-end">
                <button
                  onClick={saveEntitlement}
                  disabled={savingEntitlement}
                  className="px-6 py-2.5 bg-accent-green hover:bg-accent-green/80 disabled:opacity-50 text-white font-bold rounded-lg transition-colors text-xs"
                >
                  {savingEntitlement ? 'Saving Entitlements...' : 'Save Entitlements'}
                </button>
              </div>
            </div>
          )}

          {/* SECURITY TAB */}
          {activeTab === 'security' && (
            <div className="glass p-6 rounded-xl space-y-6">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider border-b border-dark-800 pb-3">Security & Account Access Control</h3>
              
              <div className="p-4 bg-red-500/10 border border-red-500/20 text-red-400 text-xs rounded-xl flex items-start space-x-3">
                <ShieldAlert className="w-5 h-5 flex-shrink-0" />
                <div>
                  <p className="font-bold">Dangerous Action Warning</p>
                  <p className="mt-1 opacity-90">
                    Blocking a user will write a `blocked: true` flag in Firestore and disable their record in Firebase Authentication. 
                    They will be kicked out from active mobile instances and locked out from logging in.
                  </p>
                </div>
              </div>

              <div className="flex items-center justify-between p-4 bg-dark-900 border border-dark-800 rounded-xl">
                <div>
                  <h4 className="text-xs font-bold text-white uppercase tracking-wider">
                    Account Status: {user.blocked ? 'Blocked' : 'Active'}
                  </h4>
                  <p className="text-[10px] text-dark-500 mt-1">Click the button below to toggle user login access permissions.</p>
                </div>
                
                <button
                  onClick={toggleBlock}
                  disabled={savingBlock}
                  className={`px-5 py-2.5 text-xs font-bold rounded-lg transition-colors ${
                    user.blocked 
                      ? 'bg-accent-green hover:bg-accent-green/80 text-white' 
                      : 'bg-red-500 hover:bg-red-600 text-white'
                  }`}
                >
                  {user.blocked ? 'Unblock Account' : 'Block Account'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
