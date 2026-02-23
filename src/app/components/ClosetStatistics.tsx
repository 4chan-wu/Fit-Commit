import React from 'react';
import { motion } from 'motion/react';
import { Award, AlertTriangle, Clock, TrendingDown } from 'lucide-react';

export function ClosetStatistics() {
  return (
    <div className="px-6 md:px-8 mt-12 space-y-8 pb-8">
      {/* Most Worn Items */}
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <Award className="w-5 h-5 text-yellow-500" />
          <h3 className="text-xl text-white">가장 많이 입은 옷 TOP 5</h3>
        </div>
        <p className="text-sm text-neutral-500">착용 횟수가 많은 인기 아이템들이에요</p>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
          {(() => {
            const dummyData = [
              { id: 1, name: '블랙 가죽 재킷', image: '🧥', category: '아우터', dateAdded: '2024.01.15' },
              { id: 2, name: '화이트 셔츠', image: '👔', category: '상의', dateAdded: '2024.02.10' },
              { id: 3, name: '데님 청바지', image: '👖', category: '하의', dateAdded: '2024.01.20' },
              { id: 4, name: '스니커즈', image: '👟', category: '신발', dateAdded: '2024.03.05' },
              { id: 5, name: '울 코트', image: '🧥', category: '아우터', dateAdded: '2023.11.28' }
            ];
            
            return dummyData.map((item, index) => {
              const wearCount = 25 - index * 4;
              const lastWorn = `${index + 2}일 전`;
              
              return (
                <motion.div
                  key={`most-worn-${item.id}`}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.1, duration: 0.3 }}
                  className="bg-neutral-900 border border-neutral-800 rounded-xl overflow-hidden hover:border-yellow-500/50 transition-all"
                >
                  <div className="relative">
                    <div className="absolute top-2 left-2 z-10 w-8 h-8 bg-gradient-to-br from-yellow-500 to-orange-500 rounded-full flex items-center justify-center">
                      <span className="text-white font-bold text-sm">#{index + 1}</span>
                    </div>
                    <div className="h-32 flex items-center justify-center border-b border-neutral-800 bg-neutral-950">
                      <span className="text-3xl">{item.image}</span>
                    </div>
                  </div>
                  <div className="p-3 space-y-2">
                    <p className="text-sm text-white font-medium truncate">{item.name}</p>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-yellow-400 font-semibold">{wearCount}회 착용</span>
                      <span className="text-neutral-500">{lastWorn}</span>
                    </div>
                    <div className="w-full bg-neutral-800 rounded-full h-1.5 overflow-hidden">
                      <div 
                        className="bg-gradient-to-r from-yellow-500 to-orange-500 h-full rounded-full" 
                        style={{ width: `${(wearCount / 25) * 100}%` }} 
                      />
                    </div>
                  </div>
                </motion.div>
              );
            });
          })()}
        </div>
      </div>

      {/* Items to Discard */}
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="w-5 h-5 text-orange-500" />
          <h3 className="text-xl text-white">버려야 할 옷 TOP 5</h3>
        </div>
        <p className="text-sm text-neutral-500">오래되거나 착용 빈도가 낮은 아이템들이에요</p>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
          {(() => {
            const dummyData = [
              { id: 6, name: '그레이 후드티', image: '👕', category: '상의', dateAdded: '2023.06.15' },
              { id: 7, name: '베이지 치노팬츠', image: '👖', category: '하의', dateAdded: '2023.07.22' },
              { id: 8, name: '네이비 블레이저', image: '🧥', category: '아우터', dateAdded: '2023.05.10' },
              { id: 9, name: '브라운 로퍼', image: '👞', category: '신발', dateAdded: '2023.08.03' },
              { id: 10, name: '화이트 스니커즈', image: '👟', category: '신발', dateAdded: '2023.04.18' }
            ];
            
            return dummyData.map((item, index) => {
              const reasons = [
                { text: '6개월 미착용', icon: Clock, color: 'text-red-400' },
                { text: '트렌드 아웃', icon: TrendingDown, color: 'text-orange-400' },
                { text: '5개월 미착용', icon: Clock, color: 'text-red-400' },
                { text: '4개월 미착용', icon: Clock, color: 'text-orange-400' },
                { text: '스타일 부적합', icon: TrendingDown, color: 'text-yellow-400' },
              ];
              const reason = reasons[index];
              const ReasonIcon = reason.icon;
              
              return (
                <motion.div
                  key={`to-discard-${item.id}`}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.1, duration: 0.3 }}
                  className="bg-neutral-900 border border-neutral-800 rounded-xl overflow-hidden hover:border-orange-500/50 transition-all"
                >
                  <div className="relative">
                    <div className="absolute top-2 right-2 z-10 p-1.5 bg-orange-500/90 backdrop-blur-sm rounded-full">
                      <AlertTriangle className="w-4 h-4 text-white" />
                    </div>
                    <div className="h-32 flex items-center justify-center border-b border-neutral-800 bg-neutral-950 relative">
                      <span className="text-3xl opacity-60">{item.image}</span>
                      <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
                    </div>
                  </div>
                  <div className="p-3 space-y-2">
                    <p className="text-sm text-white font-medium truncate">{item.name}</p>
                    <div className="flex items-center gap-1.5">
                      <ReasonIcon className={`w-3.5 h-3.5 ${reason.color}`} />
                      <span className={`text-xs font-medium ${reason.color}`}>{reason.text}</span>
                    </div>
                    <div className="text-xs text-neutral-500">{item.category} · {item.dateAdded} 추가</div>
                  </div>
                </motion.div>
              );
            });
          })()}
        </div>
      </div>
    </div>
  );
}
