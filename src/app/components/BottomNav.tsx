import React from 'react';
import { Home, MessageCircle, Shirt, User } from 'lucide-react';

type Page = 'home' | 'closet' | 'style-chat' | 'my';

interface BottomNavProps {
  currentPage: string;
  onNavigate: (page: Page) => void;
}

const navItems = [
  { id: 'home' as Page, label: '홈', icon: Home },
  { id: 'closet' as Page, label: '옷장', icon: Shirt },
  { id: 'style-chat' as Page, label: '채팅', icon: MessageCircle },
  { id: 'my' as Page, label: 'MY', icon: User },
];

export default function BottomNav({ currentPage, onNavigate }: BottomNavProps) {
  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-neutral-950 border-t border-neutral-800 max-w-md mx-auto">
      <div className="grid grid-cols-4">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentPage === item.id;

          return (
            <button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              className={`flex flex-col items-center gap-1 py-3 transition-colors ${
                isActive ? 'text-white' : 'text-neutral-500 hover:text-neutral-400'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span className="text-xs">{item.label}</span>
              {isActive && (
                <div className="w-1 h-1 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full mt-0.5" />
              )}
            </button>
          );
        })}
      </div>
    </nav>
  );
}