import React from 'react';
import { Home, MessageCircle, Shirt, User } from 'lucide-react';

type Page = 'home' | 'closet' | 'style-chat' | 'my';

interface BottomNavProps {
  currentPage: string;
  onNavigate: (page: Page) => void;
}

const navItems = [
  { id: 'home' as Page, label: 'Home', icon: Home },
  { id: 'closet' as Page, label: 'Closet', icon: Shirt },
  { id: 'style-chat' as Page, label: 'Chat', icon: MessageCircle },
  { id: 'my' as Page, label: 'My', icon: User },
];

export default function BottomNav({ currentPage, onNavigate }: BottomNavProps) {
  return (
    <nav className="fixed bottom-0 left-0 right-0 z-50 border-t border-neutral-800/90 bg-neutral-950/95 backdrop-blur-xl md:hidden">
      <div className="mx-auto grid max-w-md grid-cols-4 pb-[max(env(safe-area-inset-bottom),0.5rem)] px-1 pt-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentPage === item.id;

          return (
            <button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              aria-label={item.label}
              className={`flex min-h-14 flex-col items-center justify-center gap-1 rounded-xl px-1 transition-all ${
                isActive ? 'text-white' : 'text-neutral-500 hover:text-neutral-300 active:scale-95'
              }`}
            >
              <div
                className={`flex h-9 w-9 items-center justify-center rounded-xl transition-all ${
                  isActive
                    ? 'bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 shadow-md shadow-blue-900/35'
                    : 'bg-neutral-900/0'
                }`}
              >
                <Icon className="h-[18px] w-[18px]" />
              </div>
              <span className="text-[11px] font-medium leading-none tracking-tight">{item.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
