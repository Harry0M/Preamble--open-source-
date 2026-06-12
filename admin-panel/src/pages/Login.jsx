import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Loader2 } from 'lucide-react';

const firebaseConfig = {
  apiKey: "AIzaSyAKyyBqDYnFpfk8nazu89RYhjBo31poMpA",
  authDomain: "preambl-fbea6.firebaseapp.com",
  projectId: "preambl-fbea6",
  storageBucket: "preambl-fbea6.firebasestorage.app",
  messagingSenderId: "195921517707",
  appId: "1:195921517707:web:0df0e06ddb70d1cb30088e"
};

export default function Login({ onLoginSuccess }) {
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    // Initialize Firebase Client if loaded from CDN
    if (window.firebase) {
      if (!window.firebase.apps.length) {
        window.firebase.initializeApp(firebaseConfig);
      }
    }
  }, []);

  const handleGoogleLogin = async () => {
    if (!window.firebase) {
      setError("Firebase SDK failed to load from CDN. Check connection.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const firebase = window.firebase;
      const provider = new firebase.auth.GoogleAuthProvider();
      provider.setCustomParameters({ prompt: 'select_account' });
      
      const result = await firebase.auth().signInWithPopup(provider);
      const idToken = await result.user.getIdToken();

      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken })
      });

      const data = await response.json();
      if (data.success) {
        onLoginSuccess(data.user);
        navigate('/dashboard');
      } else {
        throw new Error(data.error || 'Login verification failed');
      }
    } catch (err) {
      setError(err.message || 'Authentication failed. Please verify admin credentials.');
      // Terminate client session
      if (window.firebase) {
        window.firebase.auth().signOut().catch(() => {});
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-dark-950 p-4">
      <div className="glass w-full max-w-md p-8 rounded-2xl shadow-2xl flex flex-col items-center space-y-6">
        {/* Branding Logo */}
        <div className="flex flex-col items-center text-center space-y-2">
          <div className="w-16 h-16 rounded-2xl bg-accent-orange/10 border border-accent-orange/20 flex items-center justify-center mb-2">
            <ShieldCheck className="w-10 h-10 text-accent-orange" />
          </div>
          <h1 className="text-3xl font-extrabold font-heading text-white tracking-wide">Preamble</h1>
          <p className="text-xs text-dark-400 font-bold uppercase tracking-widest">Admin Portal</p>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="w-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs px-4 py-3 rounded-lg leading-relaxed text-center">
            {error}
          </div>
        )}

        {/* Google Sign-in Button */}
        <button
          onClick={handleGoogleLogin}
          disabled={loading}
          className="w-full flex items-center justify-center px-4 py-3 bg-white hover:bg-dark-300 disabled:opacity-50 text-dark-950 font-bold rounded-lg transition-all duration-200"
        >
          {loading ? (
            <>
              <Loader2 className="w-5 h-5 mr-3 animate-spin" />
              Verifying credentials...
            </>
          ) : (
            <>
              <svg viewBox="0 0 24 24" className="w-5 h-5 mr-3">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
              </svg>
              Sign in with Google
            </>
          )}
        </button>

        <p className="text-[10px] text-dark-500 font-semibold tracking-wide uppercase text-center select-none">
          Restricted access · Authorized administrators only
        </p>
      </div>
    </div>
  );
}
