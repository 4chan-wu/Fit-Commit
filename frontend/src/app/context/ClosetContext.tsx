import React, { createContext, useContext, useState, ReactNode } from 'react';

interface ClosetItem {
  id: number;
  name: string;
  category: string;
  seasons: string[];
  image: string;
  dateAdded: string;
  closetId: string;
}

interface Closet {
  id: string;
  name: string;
  description: string;
  tags: {
    categories: string[];
    seasons: string[];
  };
  itemIds: number[];
  isDefault?: boolean;
}

interface ClosetContextType {
  closets: Closet[];
  closetItems: ClosetItem[];
  setClosets: React.Dispatch<React.SetStateAction<Closet[]>>;
  setClosetItems: React.Dispatch<React.SetStateAction<ClosetItem[]>>;
}

const ClosetContext = createContext<ClosetContextType | undefined>(undefined);

const initialClosets: Closet[] = [
  {
    id: 'default-closet',
    name: '전체 옷장',
    description: '모든 아이템이 보관되는 기본 옷장입니다',
    tags: {
      categories: [],
      seasons: [],
    },
    itemIds: [],
    isDefault: true,
  }
];

export function ClosetProvider({ children }: { children: ReactNode }) {
  const [closets, setClosets] = useState<Closet[]>(initialClosets);
  const [closetItems, setClosetItems] = useState<ClosetItem[]>([]);

  return (
    <ClosetContext.Provider value={{ closets, closetItems, setClosets, setClosetItems }}>
      {children}
    </ClosetContext.Provider>
  );
}

export function useCloset() {
  const context = useContext(ClosetContext);
  if (context === undefined) {
    throw new Error('useCloset must be used within a ClosetProvider');
  }
  return context;
}
