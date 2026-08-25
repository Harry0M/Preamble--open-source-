import React, { useState, useEffect } from 'react';
import Header from '../components/Header';
import { Bot, Save, RefreshCw, AlertTriangle, Shield, Key, Cpu, Zap } from 'lucide-react';

const AVAILABLE_MODELS = [
  { value: 'gemini-2.5-flash-lite', label: 'Gemini 2.5 Flash Lite', tier: 'economy', desc: 'Fastest, cheapest. Good for simple tasks.' },
  { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash', tier: 'standard', desc: 'Balanced speed & quality. Recommended default.' },
  { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro', tier: 'premium', desc: 'Highest accuracy, slower. For complex parsing.' },
  { value: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash', tier: 'standard', desc: 'Previous-gen flash model.' },
  { value: 'mistral-small-latest', label: 'Mistral Small', tier: 'economy', desc: 'Mistral economy model.' },
  { value: 'mistral-medium-latest', label: 'Mistral Medium', tier: 'standard', desc: 'Mistral balanced model.' },
  { value: 'mistral-large-latest', label: 'Mistral Large', tier: 'premium', desc: 'Mistral premium model.' },
];

const TIER_COLORS = {
  economy: 'text-green-400 bg-green-400/10 border-green-400/20',
  standard: 'text-blue-400 bg-blue-400/10 border-blue-400/20',
  premium: 'text-purple-400 bg-purple-400/10 border-purple-400/20',
};

export default function AiConfig() {
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Editable fields
  const [parseModel, setParseModel] = useState('');
  const [chatModel, setChatModel] = useState('');
  const [killSwitch, setKillSwitch] = useState(false);

  useEffect(() => {
    fetchConfig();
  }, []);

  const fetchConfig = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/ai/config');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setConfig(data.config);
      setParseModel(data.config?.parseModel || 'gemini-2.5-flash-lite');
      setChatModel(data.config?.chatModel || 'gemini-2.5-flash');
      setKillSwitch(data.config?.killSwitch || false);
    } catch (err) {
      setError('Failed to load AI config: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const saveConfig = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const res = await fetch('/api/ai/config', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ parseModel, chatModel, killSwitch }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setSuccess('AI configuration saved successfully. Changes take effect immediately for all users.');
      setTimeout(() => setSuccess(null), 5000);
    } catch (err) {
      setError('Failed to save: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div>
        <Header title="AI Configuration" subtitle="Manage Task Parser & Chat AI models" />
        <div className="flex items-center justify-center py-20">
          <RefreshCw className="w-6 h-6 animate-spin text-dark-400" />
        </div>
      </div>
    );
  }

  return (
    <div>
      <Header title="AI Configuration" subtitle="Manage Task Parser & Chat AI models">
        <button
          onClick={saveConfig}
          disabled={saving}
          className="flex items-center px-5 py-2.5 text-sm font-bold text-white bg-accent-orange hover:bg-accent-orange/90 rounded-lg transition-all disabled:opacity-50"
        >
          {saving ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </Header>

      {error && (
        <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-center text-red-400 text-sm">
          <AlertTriangle className="w-4 h-4 mr-2 flex-shrink-0" />
          {error}
        </div>
      )}

      {success && (
        <div className="mb-6 p-4 bg-green-500/10 border border-green-500/20 rounded-lg flex items-center text-green-400 text-sm">
          <Zap className="w-4 h-4 mr-2 flex-shrink-0" />
          {success}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Task Parser Model */}
        <div className="bg-dark-900 border border-dark-800 rounded-xl p-6">
          <div className="flex items-center mb-4">
            <div className="w-10 h-10 bg-blue-500/10 rounded-lg flex items-center justify-center mr-3">
              <Cpu className="w-5 h-5 text-blue-400" />
            </div>
            <div>
              <h3 className="text-white font-bold text-sm">Task Parser Model</h3>
              <p className="text-dark-400 text-xs">Used for voice input, task sheet, and notification parsing</p>
            </div>
          </div>

          <div className="space-y-2">
            {AVAILABLE_MODELS.map((model) => (
              <label
                key={model.value}
                className={`flex items-center p-3 rounded-lg border cursor-pointer transition-all ${
                  parseModel === model.value
                    ? 'border-blue-500 bg-blue-500/10'
                    : 'border-dark-700 hover:border-dark-600'
                }`}
              >
                <input
                  type="radio"
                  name="parseModel"
                  value={model.value}
                  checked={parseModel === model.value}
                  onChange={(e) => setParseModel(e.target.value)}
                  className="sr-only"
                />
                <div className={`w-4 h-4 rounded-full border-2 mr-3 flex items-center justify-center flex-shrink-0 ${
                  parseModel === model.value ? 'border-blue-500' : 'border-dark-500'
                }`}>
                  {parseModel === model.value && <div className="w-2 h-2 rounded-full bg-blue-500" />}
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-white">{model.label}</span>
                    <span className={`text-[9px] font-bold uppercase px-1.5 py-0.5 rounded border ${TIER_COLORS[model.tier]}`}>
                      {model.tier}
                    </span>
                  </div>
                  <p className="text-xs text-dark-400 mt-0.5">{model.desc}</p>
                </div>
              </label>
            ))}
          </div>
        </div>

        {/* Chat Model */}
        <div className="bg-dark-900 border border-dark-800 rounded-xl p-6">
          <div className="flex items-center mb-4">
            <div className="w-10 h-10 bg-purple-500/10 rounded-lg flex items-center justify-center mr-3">
              <Bot className="w-5 h-5 text-purple-400" />
            </div>
            <div>
              <h3 className="text-white font-bold text-sm">Chat Model</h3>
              <p className="text-dark-400 text-xs">Used for conversational AI chat</p>
            </div>
          </div>

          <div className="space-y-2">
            {AVAILABLE_MODELS.map((model) => (
              <label
                key={model.value}
                className={`flex items-center p-3 rounded-lg border cursor-pointer transition-all ${
                  chatModel === model.value
                    ? 'border-purple-500 bg-purple-500/10'
                    : 'border-dark-700 hover:border-dark-600'
                }`}
              >
                <input
                  type="radio"
                  name="chatModel"
                  value={model.value}
                  checked={chatModel === model.value}
                  onChange={(e) => setChatModel(e.target.value)}
                  className="sr-only"
                />
                <div className={`w-4 h-4 rounded-full border-2 mr-3 flex items-center justify-center flex-shrink-0 ${
                  chatModel === model.value ? 'border-purple-500' : 'border-dark-500'
                }`}>
                  {chatModel === model.value && <div className="w-2 h-2 rounded-full bg-purple-500" />}
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-white">{model.label}</span>
                    <span className={`text-[9px] font-bold uppercase px-1.5 py-0.5 rounded border ${TIER_COLORS[model.tier]}`}>
                      {model.tier}
                    </span>
                  </div>
                  <p className="text-xs text-dark-400 mt-0.5">{model.desc}</p>
                </div>
              </label>
            ))}
          </div>
        </div>
      </div>

      {/* Kill Switch */}
      <div className={`bg-dark-900 border rounded-xl p-6 mb-8 ${killSwitch ? 'border-red-500/50' : 'border-dark-800'}`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center">
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center mr-3 ${
              killSwitch ? 'bg-red-500/20' : 'bg-dark-800'
            }`}>
              <Shield className={`w-5 h-5 ${killSwitch ? 'text-red-400' : 'text-dark-400'}`} />
            </div>
            <div>
              <h3 className="text-white font-bold text-sm">AI Kill Switch</h3>
              <p className="text-dark-400 text-xs">
                {killSwitch
                  ? 'AI is DISABLED globally. All task parsing and chat will fail.'
                  : 'AI is operating normally for all users.'}
              </p>
            </div>
          </div>
          <button
            onClick={() => setKillSwitch(!killSwitch)}
            className={`relative w-14 h-7 rounded-full transition-colors ${
              killSwitch ? 'bg-red-500' : 'bg-green-500'
            }`}
          >
            <div className={`absolute top-1 w-5 h-5 rounded-full bg-white transition-transform ${
              killSwitch ? 'left-8' : 'left-1'
            }`} />
          </button>
        </div>
        {killSwitch && (
          <div className="mt-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-xs font-medium">
            ⚠️ WARNING: Enabling kill switch will immediately disable ALL AI features across all app users. Task parsing, chat, and auto-subtask generation will stop working.
          </div>
        )}
      </div>

      {/* API Key Instructions */}
      <div className="bg-dark-900 border border-dark-800 rounded-xl p-6">
        <div className="flex items-center mb-4">
          <div className="w-10 h-10 bg-amber-500/10 rounded-lg flex items-center justify-center mr-3">
            <Key className="w-5 h-5 text-amber-400" />
          </div>
          <div>
            <h3 className="text-white font-bold text-sm">API Key Management</h3>
            <p className="text-dark-400 text-xs">How to change AI API keys for Task Parser and Chat</p>
          </div>
        </div>

        <div className="space-y-4 text-sm">
          <div className="p-4 bg-dark-800/50 rounded-lg border border-dark-700">
            <h4 className="text-white font-semibold mb-2">🔑 Gemini API Key</h4>
            <ol className="text-dark-300 space-y-1.5 text-xs list-decimal list-inside">
              <li>Go to <a href="https://console.cloud.google.com/security/secret-manager" target="_blank" rel="noreferrer" className="text-blue-400 hover:text-blue-300 underline">Google Cloud Secret Manager</a></li>
              <li>Find the secret named <code className="px-1.5 py-0.5 bg-dark-700 rounded text-xs font-mono text-amber-400">GEMINI_API_KEY</code></li>
              <li>Click "New Version", paste the new API key, then "Add New Version"</li>
              <li>Redeploy Cloud Functions: <code className="px-1.5 py-0.5 bg-dark-700 rounded text-xs font-mono text-amber-400">firebase deploy --only functions</code></li>
            </ol>
          </div>

          <div className="p-4 bg-dark-800/50 rounded-lg border border-dark-700">
            <h4 className="text-white font-semibold mb-2">🔑 Mistral API Key</h4>
            <ol className="text-dark-300 space-y-1.5 text-xs list-decimal list-inside">
              <li>Go to <a href="https://console.mistral.ai/api-keys" target="_blank" rel="noreferrer" className="text-blue-400 hover:text-blue-300 underline">Mistral Console → API Keys</a></li>
              <li>Create or rotate your API key</li>
              <li>Add it to Secret Manager as <code className="px-1.5 py-0.5 bg-dark-700 rounded text-xs font-mono text-amber-400">MISTRAL_API_KEY</code></li>
              <li>Redeploy: <code className="px-1.5 py-0.5 bg-dark-700 rounded text-xs font-mono text-amber-400">firebase deploy --only functions</code></li>
            </ol>
          </div>

          <div className="p-3 bg-blue-500/5 border border-blue-500/10 rounded-lg text-blue-300 text-xs">
            💡 <strong>Note:</strong> Model switching above is instant (stored in Firestore). API key changes require a Cloud Functions redeployment to take effect.
          </div>
        </div>
      </div>
    </div>
  );
}
