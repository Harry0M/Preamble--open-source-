import React, { useState, useEffect } from 'react';
import { 
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
  AreaChart, Area, Legend
} from 'recharts';
import { 
  TrendingUp, Activity, Terminal, ShieldAlert, Cpu, RefreshCw, ChevronDown, ChevronUp, Search
} from 'lucide-react';
import Header from '../components/Header';

export default function PosthogAnalytics() {
  const [funnel, setFunnel] = useState(null);
  const [trends, setTrends] = useState(null);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expandedEventId, setExpandedEventId] = useState(null);
  const [eventSearch, setEventSearch] = useState('');

  useEffect(() => {
    loadAllData();
  }, []);

  const loadAllData = async () => {
    setLoading(true);
    try {
      await Promise.all([
        fetchFunnel(),
        fetchTrends(),
        fetchEvents()
      ]);
    } catch (e) {
      console.error('Failed to load analytics:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await Promise.all([
        fetchFunnel(),
        fetchTrends(),
        fetchEvents()
      ]);
    } finally {
      setRefreshing(false);
    }
  };

  const fetchFunnel = async () => {
    try {
      const res = await fetch('/api/stats/posthog');
      if (res.ok) {
        const data = await res.json();
        setFunnel(data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchTrends = async () => {
    try {
      const res = await fetch('/api/stats/posthog/trends');
      if (res.ok) {
        const data = await res.json();
        setTrends(data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchEvents = async () => {
    try {
      const res = await fetch('/api/stats/posthog/events');
      if (res.ok) {
        const data = await res.json();
        setEvents(data.events || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // Funnel Formatting
  const installs = funnel?.installs ?? 0;
  const onboarded = funnel?.onboarded ?? 0;
  const synced = funnel?.synced ?? 0;

  const funnelChartData = [
    { name: 'App Installs', count: installs, percentage: 100, fill: '#4b5563' },
    { name: 'Onboarding Done', count: onboarded, percentage: installs > 0 ? Math.round((onboarded / installs) * 1000) / 10 : 0, fill: '#6366f1' },
    { name: 'Cloud Sync Link', count: synced, percentage: installs > 0 ? Math.round((synced / installs) * 1000) / 10 : 0, fill: '#FF6D00' },
  ];

  // Trends Formatting for charts
  const trendChartData = [];
  if (trends && trends.labels) {
    for (let i = 0; i < trends.labels.length; i++) {
      trendChartData.push({
        date: trends.labels[i].split('-').slice(0, 2).join(' '), // shorten label
        Created: trends.created[i] || 0,
        Completed: trends.completed[i] || 0,
        AI: trends.ai[i] || 0,
        Crashes: trends.crashes[i] || 0
      });
    }
  }

  // Filtered Events Feed
  const filteredEvents = events.filter(e => {
    if (!eventSearch.trim()) return true;
    const q = eventSearch.toLowerCase();
    return (
      (e.event || '').toLowerCase().includes(q) ||
      (e.distinct_id || '').toLowerCase().includes(q) ||
      JSON.stringify(e.properties).toLowerCase().includes(q)
    );
  });

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-10 h-10 border-4 border-accent-orange border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <Header title="PostHog Telemetry" subtitle="Inspect live product funnels, user action streams, and app crash telemetry." />
        <button
          onClick={handleRefresh}
          disabled={refreshing}
          className="flex items-center px-4 py-2.5 bg-dark-900 border border-dark-800 hover:bg-dark-800 text-white font-bold rounded-lg text-xs transition-all disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 mr-2 ${refreshing ? 'animate-spin' : ''}`} />
          {refreshing ? 'Refreshing...' : 'Refresh Logs'}
        </button>
      </div>

      {/* Conversion Funnel & General Stats Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Onboarding Funnel */}
        <div className="glass p-6 rounded-xl flex flex-col space-y-4 lg:col-span-1">
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center">
              <TrendingUp className="w-4 h-4 mr-2 text-accent-indigo" />
              Onboarding Funnel
            </h3>
            <p className="text-[10px] text-dark-500 mt-0.5">30-day conversion progression metrics.</p>
          </div>

          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={funnelChartData}
                layout="vertical"
                margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
              >
                <CartesianGrid stroke="#2c2c2c" strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" stroke="#a0a0a0" fontSize={10} tickLine={false} />
                <YAxis dataKey="name" type="category" stroke="#a0a0a0" fontSize={10} tickLine={false} width={100} />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#1e1e1e', borderColor: '#2c2c2c', borderRadius: '8px', fontSize: '11px' }}
                  formatter={(value, name, props) => [`${value} users (${props.payload.percentage}%)`, name]}
                />
                <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                  {funnelChartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Weekly Event Trends */}
        <div className="glass p-6 rounded-xl flex flex-col space-y-4 lg:col-span-2">
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center">
              <Activity className="w-4 h-4 mr-2 text-accent-green" />
              Event Volume trends (14 Days)
            </h3>
            <p className="text-[10px] text-dark-500 mt-0.5">Daily volume of task creations, completions, and AI sessions.</p>
          </div>

          <div className="h-64">
            {trendChartData.length === 0 ? (
              <div className="h-full flex items-center justify-center text-dark-500 text-xs font-semibold">
                No trend events recorded in the last 14 days.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trendChartData}>
                  <defs>
                    <linearGradient id="createdGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6366F1" stopOpacity={0.25}/>
                      <stop offset="95%" stopColor="#6366F1" stopOpacity={0.01}/>
                    </linearGradient>
                    <linearGradient id="completedGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#34A853" stopOpacity={0.25}/>
                      <stop offset="95%" stopColor="#34A853" stopOpacity={0.01}/>
                    </linearGradient>
                    <linearGradient id="aiGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#FF6D00" stopOpacity={0.25}/>
                      <stop offset="95%" stopColor="#FF6D00" stopOpacity={0.01}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="#2c2c2c" strokeDasharray="3 3" />
                  <XAxis dataKey="date" stroke="#a0a0a0" fontSize={10} tickLine={false} />
                  <YAxis stroke="#a0a0a0" fontSize={10} tickLine={false} />
                  <Tooltip contentStyle={{ backgroundColor: '#1e1e1e', borderColor: '#2c2c2c', borderRadius: '8px', fontSize: '11px' }} />
                  <Legend verticalAlign="top" height={36} wrapperStyle={{ fontSize: '11px', fontWeight: 'bold' }} />
                  <Area type="monotone" dataKey="Created" stroke="#6366F1" strokeWidth={2} fillOpacity={1} fill="url(#createdGrad)" />
                  <Area type="monotone" dataKey="Completed" stroke="#34A853" strokeWidth={2} fillOpacity={1} fill="url(#completedGrad)" />
                  <Area type="monotone" dataKey="AI" name="AI Interactions" stroke="#FF6D00" strokeWidth={2} fillOpacity={1} fill="url(#aiGrad)" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

      </div>

      {/* Safety telemetry overview row */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Crash alert card */}
        <div className="glass p-5 rounded-xl border border-dark-800 flex items-center space-x-4">
          <div className={`p-3 rounded-lg ${
            trends?.crashes?.reduce((a, b) => a + b, 0) > 0 ? 'bg-red-500/15 text-red-400' : 'bg-green-500/15 text-green-400'
          }`}>
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div>
            <h4 className="text-xs font-bold text-white uppercase tracking-wider">Stability Metrics (14d)</h4>
            <p className="text-xs text-dark-400 mt-1">
              {trends?.crashes?.reduce((a, b) => a + b, 0) > 0 
                ? `Critical Alert: ${trends.crashes.reduce((a, b) => a + b, 0)} client crash events detected.`
                : 'Excellent Stability: 0 app_crashed events logged.'
              }
            </p>
          </div>
        </div>

        {/* AI copilot efficiency */}
        <div className="glass p-5 rounded-xl border border-dark-800 flex items-center space-x-4">
          <div className="p-3 rounded-lg bg-accent-orange/15 text-accent-orange">
            <Cpu className="w-6 h-6" />
          </div>
          <div>
            <h4 className="text-xs font-bold text-white uppercase tracking-wider">AI Copilot Adoption</h4>
            <p className="text-xs text-dark-400 mt-1">
              Active users made {trends?.ai?.reduce((a, b) => a + b, 0) || 0} smart parsing invocations over the last two weeks.
            </p>
          </div>
        </div>
      </div>

      {/* Live Event Stream Logs */}
      <div className="glass rounded-xl overflow-hidden">
        <div className="p-5 border-b border-dark-800 bg-dark-900/35 flex flex-wrap gap-4 items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center">
              <Terminal className="w-4 h-4 mr-2 text-accent-orange" />
              Live User Event Feed (PostHog Logs)
            </h3>
            <p className="text-[10px] text-dark-500 mt-0.5">Real-time action stream of active user devices.</p>
          </div>

          <div className="relative w-full sm:w-72">
            <Search className="absolute left-3 top-2.5 w-4 h-4 text-dark-500" />
            <input
              type="text"
              value={eventSearch}
              onChange={e => setEventSearch(e.target.value)}
              placeholder="Search event type, distinct id..."
              className="w-full bg-dark-900 border border-dark-800 text-xs text-white placeholder-dark-500 pl-10 pr-4 py-2 rounded-lg focus:outline-none focus:border-accent-orange"
            />
          </div>
        </div>

        <div className="overflow-x-auto text-xs">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-dark-900/30 border-b border-dark-800 text-dark-500 font-bold uppercase tracking-wider">
                <th className="p-4">Event Type</th>
                <th className="p-4">Distinct User ID</th>
                <th className="p-4">Trigger Timestamp</th>
                <th className="p-4 text-center">Inspect</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-dark-800 text-dark-300">
              {filteredEvents.length === 0 ? (
                <tr>
                  <td colSpan="4" className="p-8 text-center text-dark-500 font-semibold">
                    No matching telemetry logs found.
                  </td>
                </tr>
              ) : (
                filteredEvents.map(ev => {
                  const isExpanded = expandedEventId === ev.id;
                  return (
                    <React.Fragment key={ev.id}>
                      <tr className="hover:bg-dark-900/20 transition-colors">
                        <td className="p-4 font-semibold text-white">
                          <span className={`px-2 py-0.5 rounded text-[10px] border ${
                            ev.event.startsWith('$') 
                              ? 'bg-dark-800 border-dark-700 text-dark-400'
                              : 'bg-accent-orange/15 border-accent-orange/20 text-accent-orange font-bold'
                          }`}>
                            {ev.event}
                          </span>
                        </td>
                        <td className="p-4 font-mono text-[11px] text-dark-500">
                          {ev.distinct_id}
                        </td>
                        <td className="p-4">
                          {new Date(ev.timestamp).toLocaleString()}
                        </td>
                        <td className="p-4 text-center">
                          <button
                            onClick={() => setExpandedEventId(isExpanded ? null : ev.id)}
                            className="p-1 bg-dark-800 hover:bg-dark-700 text-white rounded transition-colors"
                          >
                            {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                          </button>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr className="bg-dark-950/45">
                          <td colSpan="4" className="p-4">
                            <div className="p-4 bg-dark-950 border border-dark-900 rounded-lg overflow-x-auto">
                              <pre className="text-[10px] text-accent-green font-mono leading-relaxed max-w-full">
                                {JSON.stringify(ev.properties, null, 2)}
                              </pre>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
