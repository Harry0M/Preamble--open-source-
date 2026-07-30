import React, { useState, useEffect } from 'react';
import { Bot, Plus, Trash2, X, RefreshCw, Star, Power, Pencil, Save, ChevronDown, Key } from 'lucide-react';
import Header from '../components/Header';

const MAX_MODELS = 50;

// ─── Pre-configured provider & model catalog ─────────────────────────────────
// These match the providers implemented in model-router.ts
const PROVIDERS = [
  { id: 'google', label: 'Google (Gemini)', color: 'blue' },
  { id: 'anthropic', label: 'Anthropic (Claude)', color: 'orange' },
  { id: 'openai', label: 'OpenAI (GPT)', color: 'green' },
  { id: 'mistral', label: 'Mistral AI', color: 'purple' },
];

// Known models with pre-filled metadata. User can still enter custom IDs.
const MODEL_CATALOG = [
  // Google
  { provider: 'google', modelId: 'gemini-2.5-flash', displayName: 'Gemini 2.5 Flash', supportsReasoning: true, costPerMillionTokens: 0.15, maxContextWindow: 1048576 },
  { provider: 'google', modelId: 'gemini-2.5-pro', displayName: 'Gemini 2.5 Pro', supportsReasoning: true, costPerMillionTokens: 1.25, maxContextWindow: 1048576 },
  { provider: 'google', modelId: 'gemini-2.0-flash', displayName: 'Gemini 2.0 Flash', supportsReasoning: false, costPerMillionTokens: 0.10, maxContextWindow: 1048576 },
  // Anthropic
  { provider: 'anthropic', modelId: 'claude-sonnet-4-20250514', displayName: 'Claude Sonnet 4', supportsReasoning: true, costPerMillionTokens: 3.0, maxContextWindow: 200000 },
  { provider: 'anthropic', modelId: 'claude-haiku-3-5-20241022', displayName: 'Claude Haiku 3.5', supportsReasoning: false, costPerMillionTokens: 0.80, maxContextWindow: 200000 },
  { provider: 'anthropic', modelId: 'claude-opus-4-20250514', displayName: 'Claude Opus 4', supportsReasoning: true, costPerMillionTokens: 15.0, maxContextWindow: 200000 },
  // OpenAI
  { provider: 'openai', modelId: 'gpt-4o', displayName: 'GPT-4o', supportsReasoning: false, costPerMillionTokens: 2.50, maxContextWindow: 128000 },
  { provider: 'openai', modelId: 'gpt-4o-mini', displayName: 'GPT-4o Mini', supportsReasoning: false, costPerMillionTokens: 0.15, maxContextWindow: 128000 },
  { provider: 'openai', modelId: 'o3-mini', displayName: 'o3-mini', supportsReasoning: true, costPerMillionTokens: 1.10, maxContextWindow: 200000 },
  // Mistral
  { provider: 'mistral', modelId: 'mistral-large-latest', displayName: 'Mistral Large', supportsReasoning: false, costPerMillionTokens: 2.0, maxContextWindow: 128000 },
  { provider: 'mistral', modelId: 'mistral-small-latest', displayName: 'Mistral Small', supportsReasoning: false, costPerMillionTokens: 0.20, maxContextWindow: 128000 },
  { provider: 'mistral', modelId: 'codestral-latest', displayName: 'Codestral', supportsReasoning: false, costPerMillionTokens: 0.30, maxContextWindow: 256000 },
];

// API key environment variable names per provider
const API_KEY_ENV_VARS = {
  google: 'GOOGLE_GENAI_API_KEY',
  anthropic: 'ANTHROPIC_API_KEY',
  openai: 'OPENAI_API_KEY',
  mistral: 'MISTRAL_API_KEY',
};

const emptyModel = {
  id: '',
  provider: '',
  modelId: '',
  displayName: '',
  enabled: true,
  isDefault: false,
  costPerMillionTokens: 1.0,
  supportsReasoning: false,
  maxContextWindow: 128000,
};

export default function AiV2Models() {
  const [models, setModels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingModel, setEditingModel] = useState(null);
  const [formData, setFormData] = useState({ ...emptyModel });
  const [formError, setFormError] = useState(null);

  useEffect(() => {
    fetchModels();
  }, []);

  const fetchModels = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/v2/models');
      const data = await res.json();
      setModels(data.models || []);
    } catch (e) {
      setError('Failed to load AI V2 models');
    } finally {
      setLoading(false);
    }
  };

  const saveModels = async (updatedModels) => {
    setSaving(true);
    setError(null);
    try {
      const res = await fetch('/api/v2/models', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ models: updatedModels }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || 'Failed to save models');
        return false;
      }
      setModels(data.models);
      return true;
    } catch (e) {
      setError('Failed to save AI V2 models');
      return false;
    } finally {
      setSaving(false);
    }
  };

  const validateForm = () => {
    if (!formData.provider.trim() || formData.provider.length > 100) {
      return 'Provider name must be 1–100 characters';
    }
    if (!formData.modelId.trim() || formData.modelId.length > 200) {
      return 'Model ID must be 1–200 characters';
    }
    if (!formData.displayName.trim() || formData.displayName.length > 60) {
      return 'Display name must be 1–60 characters';
    }
    const cost = parseFloat(formData.costPerMillionTokens);
    if (isNaN(cost) || cost <= 0) {
      return 'Cost per million tokens must be a positive number';
    }
    return null;
  };

  const handleAddModel = () => {
    setFormData({ ...emptyModel });
    setEditingModel(null);
    setFormError(null);
    setShowAddModal(true);
  };

  const handleEditModel = (model) => {
    setFormData({ ...model });
    setEditingModel(model.id);
    setFormError(null);
    setShowAddModal(true);
  };

  const handleSubmitForm = async (e) => {
    e.preventDefault();
    const validationError = validateForm();
    if (validationError) {
      setFormError(validationError);
      return;
    }

    const modelEntry = {
      ...formData,
      id: editingModel || formData.modelId.replace(/[^a-zA-Z0-9._-]/g, '-'),
      costPerMillionTokens: parseFloat(formData.costPerMillionTokens),
      maxContextWindow: parseInt(formData.maxContextWindow) || 128000,
    };

    let updatedModels;
    if (editingModel) {
      updatedModels = models.map((m) => (m.id === editingModel ? modelEntry : m));
    } else {
      if (models.length >= MAX_MODELS) {
        setFormError(`Maximum ${MAX_MODELS} models allowed`);
        return;
      }
      // If this is the first model, set it as default
      if (models.length === 0) {
        modelEntry.isDefault = true;
        modelEntry.enabled = true;
      }
      updatedModels = [...models, modelEntry];
    }

    // Ensure exactly one default
    const defaults = updatedModels.filter((m) => m.isDefault);
    if (defaults.length === 0) {
      // If no default, keep existing default or set first enabled
      const firstEnabled = updatedModels.find((m) => m.enabled);
      if (firstEnabled) firstEnabled.isDefault = true;
    }

    const success = await saveModels(updatedModels);
    if (success) {
      setShowAddModal(false);
      setEditingModel(null);
    }
  };

  const handleSetDefault = async (modelId) => {
    const target = models.find((m) => m.id === modelId);
    if (!target || !target.enabled) {
      setError('Only enabled models can be set as default');
      return;
    }
    const updatedModels = models.map((m) => ({
      ...m,
      isDefault: m.id === modelId,
    }));
    await saveModels(updatedModels);
  };

  const handleToggleEnabled = async (modelId) => {
    const target = models.find((m) => m.id === modelId);
    if (!target) return;

    // Cannot disable default model without picking new default
    if (target.enabled && target.isDefault) {
      setError('Cannot disable the default model. Set another model as default first.');
      return;
    }

    const updatedModels = models.map((m) =>
      m.id === modelId ? { ...m, enabled: !m.enabled } : m
    );
    await saveModels(updatedModels);
  };

  const handleDeleteModel = async (modelId) => {
    const target = models.find((m) => m.id === modelId);
    if (!target) return;

    // Cannot delete default model
    if (target.isDefault) {
      setError('Cannot remove the default model. Set another model as default first.');
      return;
    }

    if (!window.confirm(`Delete model "${target.displayName}"?`)) return;

    const updatedModels = models.filter((m) => m.id !== modelId);
    await saveModels(updatedModels);
  };

  return (
    <div className="space-y-6">
      <Header title="AI V2 Models" subtitle="Manage the Model Registry — providers, costs, and default model configuration.">
        <div className="flex items-center space-x-3">
          <button
            onClick={fetchModels}
            disabled={loading}
            className="flex items-center px-3 py-2.5 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg text-xs transition-colors border border-dark-700"
          >
            <RefreshCw className={`w-4 h-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={handleAddModel}
            disabled={models.length >= MAX_MODELS}
            className="flex items-center px-4 py-2.5 bg-white hover:bg-dark-300 text-dark-950 font-bold rounded-lg text-xs transition-colors disabled:opacity-50"
          >
            <Plus className="w-4 h-4 mr-2" />
            Add Model
          </button>
        </div>
      </Header>

      {/* Error Banner */}
      {error && (
        <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-xs px-4 py-3 rounded-lg flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-4 text-red-400 hover:text-red-300">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Model Count Info */}
      <div className="flex items-center justify-between">
        <span className="text-xs text-dark-500 font-semibold">
          {models.length} / {MAX_MODELS} models configured
        </span>
        {saving && (
          <span className="text-xs text-accent-orange font-semibold animate-pulse">Saving...</span>
        )}
      </div>

      {/* Models Table */}
      <div className="glass rounded-xl border border-dark-800 overflow-hidden">
        {loading ? (
          <div className="py-12 text-center">
            <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            <span className="text-dark-500 text-xs font-semibold">Loading model registry...</span>
          </div>
        ) : models.length === 0 ? (
          <div className="py-12 text-center text-dark-500 text-xs font-semibold">
            No models configured. Add your first AI model to get started.
          </div>
        ) : (
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-dark-800 bg-dark-900/60">
                <th className="text-left px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Model</th>
                <th className="text-left px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Provider</th>
                <th className="text-left px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Model ID</th>
                <th className="text-center px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Cost/M</th>
                <th className="text-center px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Status</th>
                <th className="text-center px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Default</th>
                <th className="text-center px-4 py-3 font-bold text-dark-400 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody>
              {models.map((model) => (
                <tr key={model.id} className="border-b border-dark-800/50 hover:bg-dark-900/30 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center space-x-2">
                      <Bot className="w-4 h-4 text-accent-blue flex-shrink-0" />
                      <div>
                        <span className="font-bold text-white">{model.displayName}</span>
                        {model.supportsReasoning && (
                          <span className="ml-2 px-1.5 py-0.5 bg-purple-500/10 border border-purple-500/20 text-purple-400 rounded text-[9px] uppercase font-bold">
                            Reasoning
                          </span>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-dark-400 font-mono">{model.provider}</td>
                  <td className="px-4 py-3 text-dark-400 font-mono text-[10px]">{model.modelId}</td>
                  <td className="px-4 py-3 text-center text-dark-300 font-mono">${model.costPerMillionTokens}</td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => handleToggleEnabled(model.id)}
                      className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        model.enabled
                          ? 'bg-green-500/10 text-green-400 border border-green-500/20'
                          : 'bg-dark-800 text-dark-500 border border-dark-700'
                      }`}
                    >
                      {model.enabled ? 'Enabled' : 'Disabled'}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-center">
                    {model.isDefault ? (
                      <span className="inline-flex items-center px-2 py-0.5 bg-accent-orange/10 border border-accent-orange/20 text-accent-orange rounded text-[10px] font-bold">
                        <Star className="w-3 h-3 mr-1" />
                        Default
                      </span>
                    ) : (
                      <button
                        onClick={() => handleSetDefault(model.id)}
                        disabled={!model.enabled}
                        className="text-dark-500 hover:text-accent-orange text-[10px] font-bold disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                      >
                        Set Default
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center space-x-2">
                      <button
                        onClick={() => handleEditModel(model)}
                        className="p-1.5 bg-dark-900/50 hover:bg-dark-700 border border-dark-800 text-dark-400 hover:text-white rounded transition-colors"
                        title="Edit model"
                      >
                        <Pencil className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => handleDeleteModel(model.id)}
                        className="p-1.5 bg-dark-900/50 hover:bg-red-500/10 hover:text-red-400 border border-dark-800 text-dark-500 rounded transition-colors"
                        title="Delete model"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* API Keys Information */}
      <div className="glass rounded-xl border border-dark-800 p-6 space-y-4">
        <div className="flex items-center space-x-2">
          <Key className="w-4 h-4 text-accent-orange" />
          <h3 className="text-sm font-bold text-white">API Key Configuration</h3>
        </div>
        <p className="text-xs text-dark-400 leading-relaxed">
          API keys are stored as <strong className="text-white">Firebase Cloud Functions environment variables</strong> — not in the database. 
          They are never exposed to the client app.
        </p>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-dark-800">
                <th className="text-left py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">Provider</th>
                <th className="text-left py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">Environment Variable</th>
                <th className="text-left py-2 px-3 text-dark-500 font-bold uppercase tracking-wider">How to Set</th>
              </tr>
            </thead>
            <tbody>
              {PROVIDERS.map((p) => (
                <tr key={p.id} className="border-b border-dark-800/50">
                  <td className="py-2.5 px-3 text-white font-semibold">{p.label}</td>
                  <td className="py-2.5 px-3 font-mono text-accent-blue">{API_KEY_ENV_VARS[p.id]}</td>
                  <td className="py-2.5 px-3 text-dark-400">
                    <code className="bg-dark-900 px-1.5 py-0.5 rounded text-[10px]">
                      firebase functions:secrets:set {API_KEY_ENV_VARS[p.id]}
                    </code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="bg-dark-900/50 rounded-lg p-3 text-[10px] text-dark-400 leading-relaxed space-y-1">
          <p><strong className="text-dark-300">To set/update an API key:</strong></p>
          <p className="font-mono text-dark-300">firebase functions:secrets:set GOOGLE_GENAI_API_KEY</p>
          <p>Then redeploy: <span className="font-mono text-dark-300">firebase deploy --only functions</span></p>
          <p className="mt-2 text-accent-orange font-semibold">You only need keys for providers you've enabled above.</p>
        </div>
      </div>

      {/* ADD/EDIT MODEL MODAL */}
      {showAddModal && (
        <div className="fixed inset-0 bg-dark-950/75 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="glass w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
            <div className="p-4 border-b border-dark-800 bg-dark-900/40 flex items-center justify-between">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">
                {editingModel ? 'Edit Model' : 'Add Model'}
              </h3>
              <button onClick={() => setShowAddModal(false)} className="text-dark-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmitForm} className="p-6 space-y-4 text-xs overflow-y-auto max-h-[75vh]">
              {formError && (
                <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-xs px-3 py-2 rounded-lg">
                  {formError}
                </div>
              )}

              {/* Provider Selection — dropdown of implemented providers */}
              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Provider <span className="text-red-400">*</span></label>
                <select
                  required
                  value={formData.provider}
                  onChange={(e) => {
                    const newProvider = e.target.value;
                    setFormData({ ...formData, provider: newProvider, modelId: '', displayName: '', supportsReasoning: false, costPerMillionTokens: 1.0, maxContextWindow: 128000 });
                  }}
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-dark-600 appearance-none cursor-pointer"
                >
                  <option value="" disabled>Select a provider...</option>
                  {PROVIDERS.map((p) => (
                    <option key={p.id} value={p.id}>{p.label}</option>
                  ))}
                </select>
                <span className="text-dark-600 text-[10px]">Only providers with implemented adapters are shown</span>
              </div>

              {/* Model ID — dropdown of known models + custom input */}
              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Model ID <span className="text-red-400">*</span></label>
                {formData.provider && MODEL_CATALOG.filter(m => m.provider === formData.provider).length > 0 && (
                  <div className="mb-2">
                    <span className="text-dark-600 text-[10px] block mb-1">Quick pick a known model:</span>
                    <div className="flex flex-wrap gap-1.5">
                      {MODEL_CATALOG.filter(m => m.provider === formData.provider).map((catalogModel) => (
                        <button
                          key={catalogModel.modelId}
                          type="button"
                          onClick={() => setFormData({
                            ...formData,
                            modelId: catalogModel.modelId,
                            displayName: formData.displayName || catalogModel.displayName,
                            supportsReasoning: catalogModel.supportsReasoning,
                            costPerMillionTokens: catalogModel.costPerMillionTokens,
                            maxContextWindow: catalogModel.maxContextWindow,
                          })}
                          className={`px-2.5 py-1.5 rounded-md text-[10px] font-semibold border transition-all ${
                            formData.modelId === catalogModel.modelId
                              ? 'bg-white text-dark-950 border-white'
                              : 'bg-dark-900 text-dark-300 border-dark-700 hover:border-dark-500 hover:text-white'
                          }`}
                        >
                          {catalogModel.modelId}
                          {catalogModel.supportsReasoning && (
                            <span className="ml-1 text-purple-400">⚡</span>
                          )}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                <input
                  type="text"
                  required
                  maxLength={200}
                  value={formData.modelId}
                  onChange={(e) => setFormData({ ...formData, modelId: e.target.value })}
                  placeholder={formData.provider ? "Select above or type a custom model ID" : "Select a provider first"}
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-dark-600"
                />
                <span className="text-dark-600 text-[10px]">{formData.modelId.length}/200 · You can enter any model ID if the provider updates their offerings</span>
              </div>

              {/* Display Name — user decides */}
              <div className="space-y-1.5">
                <label className="text-dark-500 font-bold">Display Name <span className="text-red-400">*</span></label>
                <input
                  type="text"
                  required
                  maxLength={60}
                  value={formData.displayName}
                  onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                  placeholder="e.g. Gemini Flash, Claude Sonnet..."
                  className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-dark-600"
                />
                <span className="text-dark-600 text-[10px]">{formData.displayName.length}/60 · This is what users see in the app</span>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Cost per Million Tokens ($) <span className="text-red-400">*</span></label>
                  <input
                    type="number"
                    required
                    min="0.001"
                    step="0.001"
                    value={formData.costPerMillionTokens}
                    onChange={(e) => setFormData({ ...formData, costPerMillionTokens: e.target.value })}
                    placeholder="0.15"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-dark-600"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Max Context Window</label>
                  <input
                    type="number"
                    min="1000"
                    value={formData.maxContextWindow}
                    onChange={(e) => setFormData({ ...formData, maxContextWindow: e.target.value })}
                    placeholder="128000"
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2.5 rounded text-white focus:outline-none focus:border-dark-600"
                  />
                </div>
              </div>

              <div className="flex items-center space-x-6 pt-2">
                <label className="flex items-center space-x-2 select-none cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.enabled}
                    onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                    className="w-4 h-4 bg-dark-900 border border-dark-800 rounded"
                  />
                  <span className="text-white font-bold">Enabled</span>
                </label>
                <label className="flex items-center space-x-2 select-none cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.supportsReasoning}
                    onChange={(e) => setFormData({ ...formData, supportsReasoning: e.target.checked })}
                    className="w-4 h-4 bg-dark-900 border border-dark-800 rounded"
                  />
                  <span className="text-white font-bold">Supports Reasoning / Thinking</span>
                </label>
              </div>

              {/* Auto-filled reasoning note */}
              {formData.supportsReasoning && (
                <div className="bg-purple-500/5 border border-purple-500/20 rounded-lg px-3 py-2 text-[10px] text-purple-300">
                  ⚡ This model's thinking process will be shown as a collapsible block in the chat UI.
                </div>
              )}

              <div className="pt-4 border-t border-dark-800 flex justify-end space-x-3">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="px-5 py-2 bg-white hover:bg-dark-300 text-dark-950 font-bold rounded-lg disabled:opacity-50 flex items-center"
                >
                  <Save className="w-4 h-4 mr-2" />
                  {editingModel ? 'Save Changes' : 'Add Model'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
