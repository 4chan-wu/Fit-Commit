import React from 'react';
import { TrendingUp, AlertCircle } from 'lucide-react';
import { Button } from '../components/ui/button';

const trendingItems = [
  { id: 1, name: '베이직 화이트 티셔츠', price: '29,000원', brand: 'UNIQLO', image: '👕' },
  { id: 2, name: '슬림 블랙 데님', price: '89,000원', brand: 'Levi\'s', image: '👖' },
  { id: 3, name: '미니멀 화이트 스니커즈', price: '119,000원', brand: 'Common Projects', image: '👟' },
  { id: 4, name: '캐시미어 니트', price: '159,000원', brand: 'COS', image: '🧥' },
];

const missingItems = [
  { id: 1, name: '기본 흰 셔츠', reason: '다양한 코디에 활용도 높음', priority: 'high' },
  { id: 2, name: '블랙 슬랙스', reason: '포멀/캐주얼 모두 활용 가능', priority: 'high' },
  { id: 3, name: '가죽 벨트', reason: '전체 룩 완성도 향상', priority: 'medium' },
];

export default function ShopPage() {
  return (
    <div className="min-h-screen pb-24">
      {/* Header */}
      <div className="p-6">
        <h1 className="text-2xl text-white">상점</h1>
        <p className="text-sm text-neutral-400">스타일을 완성할 아이템을 찾아보세요</p>
      </div>

      {/* Trending Items */}
      <div className="px-6 mb-8 space-y-4">
        <div className="flex items-center gap-2">
          <TrendingUp className="w-5 h-5 text-green-400" />
          <h2 className="text-lg text-white">또래가 많이 산 상품</h2>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {trendingItems.map((item) => (
            <div
              key={item.id}
              className="bg-neutral-900 border border-neutral-800 rounded-xl overflow-hidden hover:border-neutral-700 transition-colors"
            >
              <div className="bg-neutral-800 h-32 flex items-center justify-center text-4xl">
                {item.image}
              </div>
              <div className="p-3 space-y-1">
                <p className="text-xs text-neutral-500">{item.brand}</p>
                <p className="text-sm text-white truncate">{item.name}</p>
                <p className="text-sm text-blue-400">{item.price}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Missing Items Section */}
      <div className="px-6 space-y-4">
        <div className="flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-orange-400" />
          <h2 className="text-lg text-white">내 옷장에 부족한 아이템</h2>
        </div>

        <div className="space-y-3">
          {missingItems.map((item) => (
            <div
              key={item.id}
              className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-3"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="text-white">{item.name}</h3>
                    <span
                      className={`text-xs px-2 py-0.5 rounded-full ${
                        item.priority === 'high'
                          ? 'bg-red-500/20 text-red-400'
                          : 'bg-yellow-500/20 text-yellow-400'
                      }`}
                    >
                      {item.priority === 'high' ? '높음' : '중간'}
                    </span>
                  </div>
                  <p className="text-sm text-neutral-400 mt-1">{item.reason}</p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  className="flex-1 border-neutral-700 text-neutral-300 hover:bg-neutral-800"
                >
                  최저가 보기
                </Button>
                <Button className="flex-1 bg-blue-500 hover:bg-blue-600">
                  가성비 추천
                </Button>
              </div>
            </div>
          ))}

          <div className="bg-blue-500/10 border border-blue-500/20 rounded-xl p-4">
            <p className="text-sm text-blue-400 mb-1">💡 활용 팁</p>
            <p className="text-xs text-neutral-400">
              부족한 아이템을 채우면 코디 활용도가 평균 2.5배 높아집니다
            </p>
          </div>
        </div>
      </div>

      {/* Budget Recommendation */}
      <div className="mx-6 mt-6 bg-neutral-900 border border-neutral-800 rounded-xl p-4">
        <p className="text-sm text-white mb-2">📊 예산 분석</p>
        <p className="text-xs text-neutral-400">
          선택하신 예산 범위에서 가장 효율적인 쇼핑 리스트를 추천해드려요
        </p>
      </div>
    </div>
  );
}