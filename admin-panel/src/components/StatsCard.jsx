import React from 'react';

export default function StatsCard({ title, value, icon: Icon, colorClass = 'text-white', bgClass = 'bg-dark-800' }) {
  return (
    <div className="glass p-6 rounded-xl hover:translate-y-[-2px] transition-all duration-200 shadow-lg flex items-center justify-between">
      <div className="space-y-2">
        <span className="text-xs font-bold text-dark-400 tracking-wider uppercase">{title}</span>
        <h3 className="text-3xl font-extrabold font-heading text-white">{value}</h3>
      </div>
      <div className={`p-3 rounded-lg ${bgClass} ${colorClass} flex items-center justify-center`}>
        <Icon className="w-6 h-6" />
      </div>
    </div>
  );
}
