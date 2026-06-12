import React, { useState, useRef, useEffect } from 'react';
import { Bot, Send, X, AlertTriangle } from 'lucide-react';

export default function AiCopilot() {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    {
      sender: 'ai',
      text: "Hello Admin! I'm your Preamble AI assistant. I'm here to automate and speed up any admin task. Ask me to draft announcements or clean up old tasks!"
    }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || loading) return;

    setInput('');
    setMessages(prev => [...prev, { sender: 'user', text }]);
    setLoading(true);

    try {
      const res = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: text })
      });
      const data = await res.json();

      if (data.reply) {
        // Look for JSON markdown blocks in response
        const jsonMatch = data.reply.match(/```json\n([\s\S]*?)\n```/);
        let actionMessage = null;

        if (jsonMatch) {
          try {
            const action = JSON.parse(jsonMatch[1]);
            actionMessage = await executeAiAction(action);
          } catch (e) {
            console.error('Failed to execute AI tool action:', e);
          }
        }

        // Clean up markdown text for bubble display
        const cleanText = data.reply.replace(/```json\n([\s\S]*?)\n```/, '').trim();
        if (cleanText) {
          setMessages(prev => [...prev, { sender: 'ai', text: cleanText }]);
        }
        if (actionMessage) {
          setMessages(prev => [...prev, { sender: 'ai', text: actionMessage, isStatus: true }]);
        }
      } else {
        setMessages(prev => [...prev, { sender: 'ai', text: "Error parsing reply." }]);
      }
    } catch (err) {
      setMessages(prev => [...prev, { sender: 'ai', text: "Could not reach Preamble AI." }]);
    } finally {
      setLoading(false);
    }
  };

  const executeAiAction = async (actionObj) => {
    const { action, payload } = actionObj;
    
    if (action === 'create_broadcast') {
      try {
        const res = await fetch('/api/broadcasts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: payload.title,
            description: payload.description,
            targetType: payload.targetType || 'all',
            autoNotify: true
          })
        });
        const data = await res.json();
        if (data.success) {
          return `✅ AI Action: Broadcast announcement "${payload.title}" created & sent.`;
        }
      } catch (e) { /* ignore */ }
      return `❌ AI Action: Failed to create broadcast "${payload.title}".`;
    }

    if (action === 'mass_delete_tasks') {
      try {
        const res = await fetch('/api/tasks/mass_delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            status: payload.status,
            beforeDate: payload.beforeDate
          })
        });
        const data = await res.json();
        if (data.success) {
          return `✅ AI Action: Successfully deleted ${data.deletedCount} tasks.`;
        }
      } catch (e) { /* ignore */ }
      return `❌ AI Action: Failed to execute mass task deletion.`;
    }

    return null;
  };

  return (
    <>
      {/* Floating Action Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="fixed bottom-6 right-6 w-14 h-14 bg-accent-orange text-white rounded-full flex items-center justify-center shadow-xl hover:scale-105 active:scale-95 transition-all duration-200 z-40 pulse-dot"
        style={{ animation: 'pulse-ring 2s infinite' }}
      >
        {isOpen ? <X className="w-6 h-6" /> : <Bot className="w-6 h-6" />}
      </button>

      {/* Floating Panel */}
      {isOpen && (
        <div className="fixed bottom-24 right-6 w-96 h-[500px] glass rounded-2xl flex flex-col overflow-hidden shadow-2xl z-40 animate-slide-in">
          {/* Header */}
          <div className="p-4 border-b border-dark-800 flex items-center justify-between bg-dark-900/40">
            <div className="flex items-center space-x-2">
              <Bot className="w-5 h-5 text-accent-orange" />
              <h3 className="font-bold text-white text-sm">Preamble AI Copilot</h3>
            </div>
            <span className="text-[10px] bg-accent-orange/10 border border-accent-orange/20 text-accent-orange px-1.5 py-0.5 rounded font-bold uppercase">
              Gemini 2.5 Pro
            </span>
          </div>

          {/* Messages Logs */}
          <div className="flex-1 p-4 overflow-y-auto space-y-4 text-xs">
            {messages.map((msg, index) => (
              <div
                key={index}
                className={`flex flex-col ${
                  msg.sender === 'user' ? 'items-end' : 'items-start'
                }`}
              >
                <div
                  className={`max-w-[85%] px-3 py-2.5 rounded-2xl leading-relaxed ${
                    msg.sender === 'user'
                      ? 'bg-white text-dark-950 font-medium rounded-tr-none'
                      : msg.isStatus
                      ? 'bg-green-500/10 border border-green-500/20 text-green-400 font-bold rounded-tl-none'
                      : 'bg-dark-800 text-dark-400 border border-dark-700 rounded-tl-none'
                  }`}
                >
                  {msg.text.split('\n').map((line, i) => (
                    <p key={i} className={i > 0 ? 'mt-1' : ''}>{line}</p>
                  ))}
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex items-center space-x-2 text-dark-500 font-medium">
                <span className="w-2 h-2 rounded-full bg-accent-orange animate-ping"></span>
                <span>AI is thinking...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input Bar */}
          <div className="p-4 border-t border-dark-800 bg-dark-950/40 flex items-center space-x-2">
            <input
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSend()}
              placeholder="Ask AI to automate tasks..."
              className="flex-1 bg-dark-900 border border-dark-800 text-xs text-white placeholder-dark-500 px-3.5 py-2.5 rounded-lg focus:outline-none focus:border-accent-orange transition-colors"
            />
            <button
              onClick={handleSend}
              className="p-2.5 bg-white hover:bg-dark-300 active:scale-95 text-dark-950 rounded-lg transition-all"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </>
  );
}
