import React, { useState, useEffect } from 'react';
import { FileText, AlertCircle, RefreshCw, X, CheckCircle2, ChevronRight, Loader2, Trash2 } from 'lucide-react';
import Header from '../components/Header';

export default function Reports() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filterStatus, setFilterStatus] = useState('all');
  const [selectedReport, setSelectedReport] = useState(null);
  const [showDetailModal, setShowDetailModal] = useState(false);

  // Status/Note Edit State
  const [reportStatus, setReportStatus] = useState('open');
  const [adminNote, setAdminNote] = useState('');
  const [saving, setSaving] = useState(false);

  // Pagination State
  const [limit] = useState(15);
  const [pageHistory, setPageHistory] = useState([null]);
  const [currentPageIndex, setCurrentPageIndex] = useState(0);
  const [nextOffsetId, setNextOffsetId] = useState(null);

  useEffect(() => {
    fetchReports(pageHistory[currentPageIndex]);
  }, [filterStatus, currentPageIndex]);

  const fetchReports = async (startAfterId = null) => {
    setLoading(true);
    try {
      let url = `/api/problem-reports?limit=${limit}&status=${filterStatus}`;
      if (startAfterId) {
        url += `&startAfter=${startAfterId}`;
      }

      const res = await fetch(url);
      const data = await res.json();
      setReports(data.reports || []);
      setNextOffsetId(data.nextOffsetId || null);
    } catch (e) {
      console.error('Failed to load reports:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleNextPage = () => {
    if (!nextOffsetId) return;
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

  const handleStatusFilterChange = (status) => {
    setFilterStatus(status);
    setPageHistory([null]);
    setCurrentPageIndex(0);
    setNextOffsetId(null);
  };

  const openReportDetail = (report) => {
    setSelectedReport(report);
    setReportStatus(report.status || 'open');
    setAdminNote(report.adminNote || '');
    setShowDetailModal(true);
  };

  const saveReportStatus = async () => {
    if (!selectedReport) return;
    setSaving(true);

    try {
      const res = await fetch(`/api/problem-reports/${selectedReport.id}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: reportStatus,
          adminNote: adminNote.trim()
        })
      });
      const data = await res.json();
      if (res.ok) {
        alert('Problem report updated and gate sync triggered.');
        setShowDetailModal(false);
        fetchReports(pageHistory[currentPageIndex]);
      } else {
        alert('Failed: ' + data.error);
      }
    } catch (err) {
      alert('Network save error.');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteReport = async () => {
    if (!selectedReport || selectedReport.status !== 'resolved') return;
    if (!window.confirm('Delete this resolved report and all GCS media permanently? This cannot be undone.')) return;

    setSaving(true);
    try {
      const res = await fetch(`/api/problem-reports/${selectedReport.id}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok) {
        alert(`Successfully deleted report. Removed ${data.deletedAttachments || 0} GCS files.`);
        setShowDetailModal(false);
        fetchReports(pageHistory[currentPageIndex]);
      } else {
        alert('Delete failed: ' + data.error);
      }
    } catch (err) {
      alert('Delete error.');
    } finally {
      setSaving(false);
    }
  };

  const formatBytes = (bytes) => {
    if (!bytes) return '0 KB';
    const mb = bytes / (1024 * 1024);
    if (mb >= 1) return `${mb.toFixed(1)} MB`;
    return `${Math.round(bytes / 1024)} KB`;
  };

  return (
    <div className="space-y-6">
      <Header title="Problem Reports" subtitle="Resolve user-submitted bug tickets, analyze client SDK stacktraces, and review evidence.">
        <button onClick={() => fetchReports(pageHistory[currentPageIndex])} className="p-2 bg-dark-900 hover:bg-dark-800 border border-dark-800 rounded-lg text-white">
          <RefreshCw className="w-4 h-4" />
        </button>
      </Header>

      {/* Status Filter Tabs */}
      <div className="glass p-3 rounded-xl flex items-center space-x-2 text-xs">
        <span className="text-dark-500 font-bold uppercase tracking-wider px-2">Filter Status:</span>
        {['all', 'open', 'in_progress', 'resolved'].map(status => (
          <button
            key={status}
            onClick={() => handleStatusFilterChange(status)}
            className={`px-3 py-1.5 font-bold uppercase tracking-wider rounded-lg transition-all ${
              filterStatus === status
                ? 'bg-white text-dark-950 shadow'
                : 'text-dark-400 hover:text-white hover:bg-dark-800'
            }`}
          >
            {status.replace('_', ' ')}
          </button>
        ))}
      </div>

      {/* Reports Table */}
      <div className="glass rounded-xl overflow-hidden shadow-lg">
        <div className="overflow-x-auto text-xs">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-dark-900/60 border-b border-dark-800 text-dark-400 font-bold uppercase">
                <th className="p-4">Status</th>
                <th className="p-4">Issue Description</th>
                <th className="p-4">User Account</th>
                <th className="p-4">Evidence files</th>
                <th className="p-4">Date Submitted</th>
                <th className="p-4 text-center">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-dark-800 text-dark-300">
              {loading ? (
                <tr>
                  <td colSpan="6" className="p-10 text-center">
                    <div className="w-8 h-8 border-3 border-accent-orange border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    <span className="text-dark-500 font-semibold">Loading tickets...</span>
                  </td>
                </tr>
              ) : reports.length === 0 ? (
                <tr>
                  <td colSpan="6" className="p-10 text-center text-dark-500 font-medium">
                    No problem reports found for this status.
                  </td>
                </tr>
              ) : (
                reports.map(report => (
                  <tr key={report.id} className="hover:bg-dark-900/20 transition-colors">
                    <td className="p-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border uppercase ${
                        report.status === 'resolved' 
                          ? 'bg-accent-green/10 text-accent-green border-accent-green/20' 
                          : report.status === 'in_progress' 
                          ? 'bg-accent-orange/10 text-accent-orange border-accent-orange/20'
                          : 'bg-red-500/10 text-red-400 border border-red-500/20'
                      }`}>
                        {report.status === 'in_progress' ? 'In Review' : (report.status || 'open')}
                      </span>
                    </td>
                    <td className="p-4">
                      <div className="font-bold text-white max-w-[260px] truncate">{report.title}</div>
                      <div className="text-[11px] text-dark-500 mt-1 max-w-[260px] truncate">{report.description}</div>
                    </td>
                    <td className="p-4">
                      <div className="font-semibold text-white">{report.userEmail || 'Unknown'}</div>
                      <div className="text-[10px] text-dark-500 font-mono mt-0.5">{report.uid?.substring(0, 14)}...</div>
                    </td>
                    <td className="p-4 font-semibold text-white">
                      {report.attachments?.length || 0} file(s)
                    </td>
                    <td className="p-4">
                      {new Date(report.createdAt || report.updatedAt).toLocaleDateString()}
                    </td>
                    <td className="p-4 text-center">
                      <button
                        onClick={() => openReportDetail(report)}
                        className="px-3 py-1.5 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg transition-colors"
                      >
                        Review
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Footer */}
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
              Previous
            </button>
            <button
              onClick={handleNextPage}
              disabled={!nextOffsetId || loading}
              className="flex items-center px-3.5 py-2 bg-dark-800 hover:bg-dark-700 disabled:opacity-40 text-white font-bold rounded-lg transition-colors"
            >
              Next
            </button>
          </div>
        </div>
      </div>

      {/* DETAIL MODAL */}
      {showDetailModal && selectedReport && (
        <div className="fixed inset-0 bg-dark-950/75 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="glass w-full max-w-2xl rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
            <div className="p-4 border-b border-dark-800 bg-dark-900/40 flex items-center justify-between">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider truncate max-w-[480px]">
                Report: {selectedReport.title}
              </h3>
              <button onClick={() => setShowDetailModal(false)} className="text-dark-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-4 text-xs overflow-y-auto max-h-[70vh]">
              {/* Grid info */}
              <div className="grid grid-cols-2 gap-4 border-b border-dark-800 pb-4">
                <div>
                  <span className="text-dark-500 font-bold block mb-1">User Account</span>
                  <span className="text-white font-medium">{selectedReport.userEmail || selectedReport.uid}</span>
                </div>
                <div>
                  <span className="text-dark-500 font-bold block mb-1">Device Details</span>
                  <span className="text-white font-medium capitalize">
                    {selectedReport.device || 'Unknown'} (SDK {selectedReport.androidSdk || 'N/A'})
                  </span>
                </div>
              </div>

              {/* Description */}
              <div className="space-y-1">
                <span className="text-dark-500 font-bold block">User Description</span>
                <p className="p-3 bg-dark-900 border border-dark-800 text-dark-300 rounded leading-relaxed whitespace-pre-wrap">
                  {selectedReport.description}
                </p>
              </div>

              {/* Evidence attachments */}
              <div className="space-y-2">
                <span className="text-dark-500 font-bold block">Attachments</span>
                {(!selectedReport.attachments || selectedReport.attachments.length === 0) ? (
                  <span className="text-dark-600 italic">No media evidence attached.</span>
                ) : (
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                    {selectedReport.attachments.map((file, i) => {
                      const isImage = file.contentType?.startsWith('image/');
                      return (
                        <a
                          key={i}
                          href={file.signedUrl || '#'}
                          target="_blank"
                          rel="noreferrer"
                          className="group bg-dark-900 border border-dark-800 hover:border-accent-orange p-2.5 rounded-lg flex flex-col items-center justify-between text-center transition-all overflow-hidden"
                        >
                          {isImage && file.signedUrl ? (
                            <img src={file.signedUrl} alt={file.name} className="w-full h-20 object-cover rounded mb-2 group-hover:scale-[1.02] transition-transform" />
                          ) : (
                            <div className="w-full h-20 bg-dark-950 border border-dark-850 flex items-center justify-center text-dark-500 font-bold mb-2">
                              File
                            </div>
                          )}
                          <span className="text-[10px] text-white truncate w-full font-semibold">{file.name}</span>
                          <span className="text-[9px] text-dark-500 mt-0.5">{formatBytes(file.sizeBytes)}</span>
                        </a>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Status form */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-dark-800 pt-4">
                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Change Ticket Status</label>
                  <select
                    value={reportStatus}
                    onChange={e => setReportStatus(e.target.value)}
                    className="w-full bg-dark-900 border border-dark-800 text-white px-3 py-2 rounded focus:outline-none"
                  >
                    <option value="open">Open</option>
                    <option value="in_progress">In Review / In Progress</option>
                    <option value="resolved">Resolved</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-dark-500 font-bold">Admin Note Visible to User</label>
                  <textarea
                    value={adminNote}
                    onChange={e => setAdminNote(e.target.value)}
                    placeholder="Provide details about the bug fix..."
                    className="w-full bg-dark-900 border border-dark-800 px-3 py-2 rounded text-white focus:outline-none focus:border-accent-orange"
                    rows="2"
                  />
                </div>
              </div>
            </div>

            {/* Modal footer */}
            <div className="p-4 border-t border-dark-800 bg-dark-900/20 flex items-center justify-between">
              {selectedReport.status === 'resolved' ? (
                <button
                  onClick={handleDeleteReport}
                  disabled={saving}
                  className="px-4 py-2 bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500 hover:text-white font-bold rounded-lg text-xs transition-colors flex items-center"
                >
                  <Trash2 className="w-4 h-4 mr-1.5" />
                  Delete Ticket
                </button>
              ) : (
                <div />
              )}
              
              <div className="flex space-x-2">
                <button
                  onClick={() => setShowDetailModal(false)}
                  className="px-4 py-2 bg-dark-800 hover:bg-dark-700 text-white font-bold rounded-lg text-xs"
                >
                  Cancel
                </button>
                <button
                  onClick={saveReportStatus}
                  disabled={saving}
                  className="px-5 py-2 bg-accent-green hover:bg-accent-green/80 disabled:opacity-50 text-white font-bold rounded-lg text-xs transition-colors"
                >
                  {saving ? 'Saving...' : 'Save Resolution'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
