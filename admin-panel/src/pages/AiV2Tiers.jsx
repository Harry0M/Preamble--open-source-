import React, { useState, useEffect } from 'react';
import { Coins, Save, RefreshCw, Info } from 'lucide-react';
import Header from '../components/Header';

const TIER_LABELS = {
  pro_student: 'Pro Student',
  pro_youth: 'Pro Youth',
  pro_standard: 'Pro Standard'
};

const TIER_COLORS = {
  pro_student: 'accent-blue',
  pro_youth: 'accent-orange',
  pro_standard: 'accent-green'
};

export default function AiV2Tiers() {
  const [tierBudgets, setTierBudgets] = useState({
    pro_student: 50000,
    pro_youth: 100000,
    pro_standard: 200000
  });
  const [models, setModels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/v2/tier-budgets');
      const data = await res.json();
      if (data.tierBudgets) setTierBudgets(data.tierBudgets);
      if (data.models) setModels(data.models);
      setDirty(false);
    } catch (e) {
      console.error('Failed to load tier budgets:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleBudgetChange = (tier, value) => {
    const numValue = value === '' ? '' : parseInt(value, 10);
    setTierBudgets(prev => ({ ...prev, [tier]: numValue }));
    setDirty(true);
  };

  const handleSave = async () => {
    // Validate all budgets are positive numbers
    for (const tier of Object.keys(TIER_LABELS)) {
      if (!tierBudgets[tier] || tierBudgets[tier] <= 0) {
        alert(`Budget for ${TIER_LABELS[tier]} must be a positive number.`);
        return;
      }
    }

    setSaving(true);
    try {
      const res = await fetch('/api/v2/tier-budgets', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tierBudgets })
      });
      const data = await res.json();
      if (data.success) {
        setDirty(false);
        alert('Tier budgets saved. Changes apply from the next budget reset cycle.');
      } else {
        alert(data.error || 'Failed to save tier budgets.');
      }
    } catch (err) {
      alert('Failed to save tier budgets.');
    } finally {
      setSaving(false);
    }
  };

  const formatTokens = (count) => {
    if (count >= 1000000) return `${(count / 1000000).toFixed(1)}M`;
    if (count >= 1000) return `${(count / 1000).toFixed(0)}K`;
    return count.toString();
  };

  return (
    <div className="space-y-6">
      <Header title="AI V2 — Tier Budgets" subtitle="Manage daily token budgets per subscription tier.">
        <button
          onClick={fetchData}
          disabled={loading}
          className="flex items-center px-4 py-2.5 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg text-xs transition-colors mr-2"
        >
          <RefreshCw className={`w-4 h-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </Header>

      {loading ? (
        <div className="py-12 text-center">
          <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
          <span className="text-dark-500 text-xs font-semibold">Loading tier budgets...</span>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Info Banner */}
          <div className="glass p-4 rounded-xl border border-dark-800 flex items-start space-x-3">
            <Info className="w-5 h-5 text-accent-blue flex-shrink-0 mt-0.5" />
            <div className="text-xs text-dark-400 leading-relaxed">
              <p className="font-bold text-white mb-1">How daily budgets work</p>
              <p>Each user's daily AI usage is tracked in <strong className="text-white">normalized tokens</strong> — the actual token count multiplied by the model's cost rate. Users on cheaper models consume budget slower; expensive models consume faster.</p>
              <p className="mt-1 text-accent-orange font-semibold">Changes apply from the next budget reset cycle.</p>
            </div>
          </div>

          {/* Tier Budget Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {Object.entries(TIER_LABELS).map(([tier, label]) => {
              const color = TIER_COLORS[tier];
              return (
                <div key={tier} className="glass p-6 rounded-xl border border-dark-800 space-y-4">
                  <div className="flex items-center space-x-3">
                    <div className={`w-10 h-10 rounded-lg bg-${color}/10 border border-${color}/20 flex items-center justify-center`}>
                      <Coins className={`w-5 h-5 text-${color}`} />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-white">{label}</h3>
                      <p className="text-[10px] text-dark-500 uppercase tracking-wider font-semibold">{tier}</p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <label className="text-dark-400 text-xs font-bold">Daily Token Budget (normalized)</label>
                    <input
                      type="number"
                      min="1"
                      value={tierBudgets[tier]}
                      onChange={e => handleBudgetChange(tier, e.target.value)}
                      className="w-full bg-dark-900 border border-dark-800 px-4 py-3 rounded-lg text-white text-sm font-mono focus:outline-none focus:border-dark-600 transition-colors"
                    />
                    <p className="text-[10px] text-dark-500 font-semibold">
                      ≈ {formatTokens(tierBudgets[tier])} normalized tokens / day
                    </p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Save Button */}
          <div className="flex justify-end">
            <button
              onClick={handleSave}
              disabled={!dirty || saving}
              className={`flex items-center px-6 py-3 font-bold rounded-lg text-xs transition-all ${
                dirty
                  ? 'bg-white hover:bg-dark-300 text-dark-950 shadow-md shadow-white/5'
                  : 'bg-dark-800 text-dark-500 cursor-not-allowed'
              }`}
            >
              <Save className="w-4 h-4 mr-2" />
              {saving ? 'Saving...' : 'Save Budgets'}
            </button>
          </div>

          {/* Model Cost Reference Table */}
          <div className="glass p-6 rounded-xl border border-dark-800 space-y-4">
            <h3 className="text-sm font-bold text-white">Model Cost Reference</h3>
            <p className="text-xs text-dark-400">
              Current cost rates per model. These rates determine how fast each model consumes a user's daily budget.
            </p>

            {models.length === 0 ? (
              <p className="text-xs text-dark-500 font-semibold py-4 text-center">
                No models configured. Add models in the AI V2 Models page.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-dark-800">
                      <th className="text-left py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">Model</th>
                      <th className="text-left py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">Provider</th>
                      <th className="text-right py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">Cost / 1M tokens</th>
                      <th className="text-right py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {models.map((m, idx) => (
                      <tr key={idx} className="border-b border-dark-800/50 hover:bg-dark-900/50 transition-colors">
                        <td className="py-2.5 px-3 text-white font-semibold">{m.displayName}</td>
                        <td className="py-2.5 px-3 text-dark-400">{m.provider}</td>
                        <td className="py-2.5 px-3 text-right font-mono text-white">${m.costPerMillionTokens}</td>
                        <td className="py-2.5 px-3 text-right">
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            m.enabled
                              ? 'bg-green-500/10 text-green-400 border border-green-500/20'
                              : 'bg-dark-800 text-dark-500 border border-dark-700'
                          }`}>
                            {m.enabled ? 'Enabled' : 'Disabled'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Budget consumption examples */}
            {models.filter(m => m.enabled).length > 0 && (
              <div className="mt-4 pt-4 border-t border-dark-800">
                <h4 className="text-xs font-bold text-dark-400 mb-2">Estimated requests per day (at ~1500 tokens/request)</h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  {Object.entries(TIER_LABELS).map(([tier, label]) => {
                    const budget = tierBudgets[tier] || 0;
                    const cheapest = models.filter(m => m.enabled).sort((a, b) => a.costPerMillionTokens - b.costPerMillionTokens)[0];
                    const expensive = models.filter(m => m.enabled).sort((a, b) => b.costPerMillionTokens - a.costPerMillionTokens)[0];
                    const avgTokensPerReq = 1500;
                    const cheapReqs = cheapest ? Math.floor(budget / (avgTokensPerReq * cheapest.costPerMillionTokens / 1000000)) : 0;
                    const expReqs = expensive ? Math.floor(budget / (avgTokensPerReq * expensive.costPerMillionTokens / 1000000)) : 0;

                    return (
                      <div key={tier} className="bg-dark-900/50 rounded-lg p-3 border border-dark-800/50">
                        <p className="text-[10px] text-dark-500 font-bold uppercase mb-1">{label}</p>
                        {cheapest && (
                          <p className="text-xs text-dark-300">
                            <span className="text-white font-bold">{cheapReqs.toLocaleString()}</span> reqs ({cheapest.displayName})
                          </p>
                        )}
                        {expensive && expensive.modelId !== cheapest?.modelId && (
                          <p className="text-xs text-dark-300">
                            <span className="text-white font-bold">{expReqs.toLocaleString()}</span> reqs ({expensive.displayName})
                          </p>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
