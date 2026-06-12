import React, { useState, useEffect } from 'react';
import { UserSquare2, Plus, Trash2, Eye, Loader2, X } from 'lucide-react';
import Header from '../components/Header';

export default function Groups() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showMembersModal, setShowMembersModal] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [members, setMembers] = useState([]);
  const [loadingMembers, setLoadingMembers] = useState(false);

  // Group Builder Form State
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [filterType, setFilterType] = useState('manual');
  const [filterGender, setFilterGender] = useState('');
  const [filterAgeMin, setFilterAgeMin] = useState('');
  const [filterAgeMax, setFilterAgeMax] = useState('');
  const [filterVersionMin, setFilterVersionMin] = useState('');

  useEffect(() => {
    fetchGroups();
  }, []);

  const fetchGroups = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/groups');
      const data = await res.json();
      setGroups(data.groups || []);
    } catch (e) {
      console.error('Failed to load groups:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGroup = async (e) => {
    e.preventDefault();
    if (!name.trim()) return;

    try {
      const res = await fetch('/api/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: name.trim(),
          description: description.trim() || null,
          filterType,
          filterGender: filterGender || null,
          filterAgeMin: filterAgeMin || null,
          filterAgeMax: filterAgeMax || null,
          filterVersionMin: filterVersionMin || null
        })
      });
      const data = await res.json();
      if (data.success) {
        alert('Group created successfully!');
        setName('');
        setDescription('');
        setFilterType('manual');
        setFilterGender('');
        setFilterAgeMin('');
        setFilterAgeMax('');
        setFilterVersionMin('');
        setShowCreateModal(false);
        fetchGroups();
      }
    } catch (err) {
      alert('Failed to save group.');
    }
  };

  const handleDeleteGroup = async (id, name) => {
    if (!window.confirm(`Delete the user group "${name}"? This will not affect the users themselves.`)) return;

    try {
      const res = await fetch(`/api/groups/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        alert('Group deleted.');
        fetchGroups();
      }
    } catch (err) {
      alert('Deletion failed.');
    }
  };

  const inspectMembers = async (group) => {
    setSelectedGroup(group);
    setShowMembersModal(true);
    setLoadingMembers(true);
    setMembers([]);

    try {
      const res = await fetch(`/api/groups/${group.id}/members`);
      const data = await res.json();
      setMembers(data.uids || []);
    } catch (e) {
      console.error('Error fetching members:', e);
    } finally {
      setLoadingMembers(false);
    }
  };

  return (
    <div className="space-y-6">
      <Header title="User Groups" subtitle="Define cohorts of users to target for rich notifications or announcements.">
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center px-4 py-2.5 bg-white hover:bg-dark-300 text-dark-950 font-bold rounded-lg text-xs transition-colors"
        >
          <Plus className="w-4 h-4 mr-2" />
          Create Group
        </button>
      </Header>

      {/* Groups List */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {loading ? (
          <div className="col-span-full py-12 text-center">
            <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            <span className="text-dark-500 text-xs font-semibold">Loading cohorts...</span>
          </div>
        ) : groups.length === 0 ? (
          <div className="col-span-full glass p-8 text-center text-dark-500 text-xs font-semibold">
            No user cohorts defined yet. Tap "Create Group" to build one.
          </div>
        ) : (
          groups.map(group => (
            <div key={group.id} className="glass p-5 rounded-xl flex flex-col justify-between space-y-4 shadow-lg">
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="px-2 py-0.5 bg-accent-orange/15 border border-accent-orange/20 text-accent-orange rounded text-[9px] uppercase font-bold tracking-wider">
                    {group.filterType}
                  </span>
                  <span className="text-[10px] text-dark-500 font-mono">
                    {new Date(group.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <h4 className="text-sm font-bold text-white tracking-wide">{group.name}</h4>
                {group.description && <p className="text-xs text-dark-400 leading-relaxed">{group.description}</p>}
                
                {/* Rules Display */}
                {group.filterType !== 'manual' && (
                  <div className="pt-2 flex flex-wrap gap-1.5 text-[9px] font-bold">
                    {group.filterGender && (
                      <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                        Gender: {group.filterGender}
                      </span>
                    )}
                    {(group.filterAgeMin != null || group.filterAgeMax != null) && (
                      <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                        Age: {group.filterAgeMin || 0} - {group.filterAgeMax || '∞'}
                      </span>
                    )}
                    {group.filterVersionMin && (
                      <span className="px-1.5 py-0.5 bg-dark-900 border border-dark-800 text-dark-400 rounded">
                        Min Version: Code {group.filterVersionMin}
                      </span>
                    )}
                  </div>
                )}
              </div>

              <div className="pt-4 border-t border-dark-800 flex items-center justify-between">
                <button
                  onClick={() => inspectMembers(group)}
                  className="flex items-center text-xs font-bold text-white hover:text-accent-orange transition-colors"
                >
                  <Eye className="w-4 h-4 mr-1.5" />
                  View Members
                </button>
                <button
                  onClick={() => handleDeleteGroup(group.id, group.name)}
                  className="p-1.5 bg-dark-900/50 hover:bg-red-500/10 hover:text-red-400 border border-dark-800 text-dark-500 rounded transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {/* CREATE GROUP MODAL */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-dark-950/75 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="glass w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
            <div className="p-4 border-b border-dark-800 bg-dark-900/40 flex items-center justify-between">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">Create User Group</h3>
              <button onClick={() => setShowCreateModal(false)} className="text-dark-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateGroup} className="p-6 space-y-4 text-xs">
              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Group Name</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder="e.g. Young Adults, Legacy App Users"
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Description (Optional)</label>
                <textarea
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="Describe the target audience..."
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-accent-orange"
                  rows="3"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Filter Segmentation Method</label>
                <select
                  value={filterType}
                  onChange={e => setFilterType(e.target.value)}
                  className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2.5 rounded focus:outline-none"
                >
                  <option value="manual">Manual UIDs (Static list)</option>
                  <option value="gender">Filter by Gender</option>
                  <option value="age">Filter by Age</option>
                  <option value="version">Filter by Client Version</option>
                  <option value="auto">Compound Rules (Mix fields)</option>
                </select>
              </div>

              {/* Dynamic Filter Controls */}
              {filterType !== 'manual' && (
                <div className="grid grid-cols-2 gap-4 border border-dark-800 bg-dark-900/10 p-4 rounded-xl">
                  {/* Gender rule */}
                  {(filterType === 'gender' || filterType === 'auto') && (
                    <div className="col-span-2 space-y-1.5">
                      <label className="text-dark-500 font-bold">Target Gender</label>
                      <select
                        value={filterGender}
                        onChange={e => setFilterGender(e.target.value)}
                        className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2 rounded focus:outline-none"
                      >
                        <option value="">Select Gender...</option>
                        <option value="male">Male</option>
                        <option value="female">Female</option>
                        <option value="other">Other</option>
                      </select>
                    </div>
                  )}

                  {/* Age rules */}
                  {(filterType === 'age' || filterType === 'auto') && (
                    <>
                      <div className="space-y-1.5">
                        <label className="text-dark-500 font-bold">Min Age</label>
                        <input
                          type="number"
                          value={filterAgeMin}
                          onChange={e => setFilterAgeMin(e.target.value)}
                          placeholder="e.g. 18"
                          className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-dark-500 font-bold">Max Age</label>
                        <input
                          type="number"
                          value={filterAgeMax}
                          onChange={e => setFilterAgeMax(e.target.value)}
                          placeholder="e.g. 35"
                          className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                        />
                      </div>
                    </>
                  )}

                  {/* Version rules */}
                  {(filterType === 'version' || filterType === 'auto') && (
                    <div className="col-span-2 space-y-1.5">
                      <label className="text-dark-500 font-bold">Minimum App Version Code</label>
                      <input
                        type="number"
                        value={filterVersionMin}
                        onChange={e => setFilterVersionMin(e.target.value)}
                        placeholder="e.g. 8"
                        className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none"
                      />
                    </div>
                  )}
                </div>
              )}

              <div className="pt-4 border-t border-dark-800 flex justify-end space-x-3">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-white hover:bg-dark-300 text-dark-955 font-bold rounded-lg"
                >
                  Save Group
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* INSPECT MEMBERS MODAL */}
      {showMembersModal && (
        <div className="fixed inset-0 bg-dark-950/75 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="glass w-full max-w-md rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
            <div className="p-4 border-b border-dark-800 bg-dark-900/40 flex items-center justify-between">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider truncate max-w-[280px]">
                Members: {selectedGroup?.name}
              </h3>
              <button onClick={() => setShowMembersModal(false)} className="text-dark-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 max-h-[360px] overflow-y-auto space-y-2 text-xs">
              {loadingMembers ? (
                <div className="py-8 text-center flex flex-col items-center justify-center">
                  <Loader2 className="w-6 h-6 text-accent-orange animate-spin mb-2" />
                  <span className="text-dark-500 font-semibold">Resolving cohorts query...</span>
                </div>
              ) : members.length === 0 ? (
                <div className="py-8 text-center text-dark-500 font-medium">
                  No active users match this group's parameters.
                </div>
              ) : (
                <>
                  <div className="text-[10px] text-dark-500 uppercase font-bold tracking-wider mb-2">
                    Total Members: {members.length}
                  </div>
                  <div className="space-y-1">
                    {members.map(uid => (
                      <div 
                        key={uid} 
                        className="p-2 bg-dark-900 border border-dark-800 text-dark-300 rounded font-mono text-[10px] cursor-pointer hover:border-accent-orange hover:text-white transition-colors"
                        onClick={() => {
                          setShowMembersModal(false);
                          navigate(`/users/${uid}`);
                        }}
                      >
                        {uid}
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>

            <div className="p-4 border-t border-dark-800 bg-dark-900/20 flex justify-end">
              <button
                onClick={() => setShowMembersModal(false)}
                className="px-4 py-2 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg text-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
