import React from 'react';

export default function Header({ title, subtitle, children }) {
  return (
    <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-6 mb-6 border-b border-dark-800">
      <div className="space-y-1">
        <h2 className="text-2xl font-bold font-heading text-white tracking-tight">{title}</h2>
        {subtitle && <p className="text-sm text-dark-400">{subtitle}</p>}
      </div>
      {children && (
        <div className="mt-4 sm:mt-0 flex items-center space-x-3">
          {children}
        </div>
      )}
    </header>
  );
}
