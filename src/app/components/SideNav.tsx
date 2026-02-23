import React from 'react';
import { Home, MessageCircle, Shirt, User, Sparkles } from 'lucide-react';
import { motion } from 'motion/react';

type Page = 'home' | 'closet' | 'style-chat' | 'my';

interface SideNavProps {
  currentPage: string;
  onNavigate: (page: Page) => void;
}

const navItems = [
  { id: 'home' as Page, label: '홈', icon: Home },
  { id: 'closet' as Page, label: '옷장', icon: Shirt },
  { id: 'style-chat' as Page, label: '채팅', icon: MessageCircle },
  { id: 'my' as Page, label: 'MY', icon: User },
];

export default function SideNav({ currentPage, onNavigate }: SideNavProps) {
  return (
    <aside className="hidden md:flex flex-col w-64 border-r border-neutral-800 bg-neutral-950 h-screen sticky top-0">
      {/* Logo */}
      <div className="p-6 border-b border-neutral-800">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-xl flex items-center justify-center">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <h1 className="text-xl text-white font-semibold">Fit Commit</h1>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-4 space-y-2">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentPage === item.id;

          return (
            <motion.button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all ${
                isActive
                  ? 'bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 text-white shadow-lg'
                  : 'text-neutral-400 hover:text-white hover:bg-neutral-900'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span>{item.label}</span>
            </motion.button>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-neutral-800">
        <div className="bg-neutral-900 rounded-xl p-4 space-y-2">
          <p className="text-sm text-white">AI 스타일 진단</p>
          <p className="text-xs text-neutral-400">
            내 체형과 피부톤을 분석하고 맞춤 스타일을 추천받아보세요
          </p>
        </div>
      </div>
    </aside>
  );
}
