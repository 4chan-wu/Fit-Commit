import React from 'react';
import { Sparkles, ChevronRight, ThumbsUp, ThumbsDown } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion } from 'motion/react';
import { useCloset } from '../context/ClosetContext';

interface HomePageProps {
  onStartDiagnosis: () => void;
}

const outfitRecommendations = [
  {
    id: 1,
    title: '미니멀 데일리',
    items: ['화이트 옥스포드 셔츠', '베이지 치노 팬츠', '화이트 스니커즈'],
    reason: '깔끔하고 단정한 이미지로 어디서나 무난합니다',
    colors: ['bg-neutral-100', 'bg-amber-100', 'bg-white'],
  },
  {
    id: 2,
    title: '캐주얼 댄디',
    items: ['네이비 니트', '그레이 슬랙스', '브라운 로퍼'],
    reason: '차분한 톤으로 지적이면서도 편안한 느낌',
    colors: ['bg-blue-900', 'bg-gray-400', 'bg-amber-800'],
  },
  {
    id: 3,
    title: '스트릿 캐주얼',
    items: ['블랙 후디', '데님 팬츠', '블랙 스니커즈'],
    reason: '활동적이면서 트렌디한 스타일',
    colors: ['bg-black', 'bg-blue-600', 'bg-neutral-900'],
  },
];

export default function HomePage({ onStartDiagnosis }: HomePageProps) {
  const { closetItems } = useCloset();

  // Helper function to get items by category
  const getItemsByCategory = (category: string) => {
    return closetItems.filter(item => item.category === category);
  };

  // Best outfit combinations (using actual closet items)
  const getBestOutfits = () => {
    const tops = getItemsByCategory('상의');
    const bottoms = getItemsByCategory('하의');
    const shoes = getItemsByCategory('신발');

    const bestCombinations = [
      {
        id: 1,
        title: '베스트 코디 #1',
        reason: '색상 조화가 완벽하고 계절감도 잘 맞아요',
        items: [
          tops[0] || null,
          bottoms[0] || null,
          shoes[0] || null,
        ]
      },
      {
        id: 2,
        title: '베스트 코디 #2',
        reason: '깔끔한 실루엣과 세련된 컬러 매칭',
        items: [
          tops[1] || null,
          bottoms[1] || null,
          shoes[1] || null,
        ]
      },
      {
        id: 3,
        title: '베스트 코디 #3',
        reason: '트렌디하면서도 편안한 스타일',
        items: [
          tops[2] || null,
          bottoms[2] || null,
          shoes[2] || null,
        ]
      }
    ];

    return bestCombinations;
  };

  // Worst outfit combinations (using actual closet items)
  const getWorstOutfits = () => {
    const tops = getItemsByCategory('상의');
    const bottoms = getItemsByCategory('하의');
    const shoes = getItemsByCategory('신발');

    const worstCombinations = [
      {
        id: 1,
        title: '워스트 코디 #1',
        reason: '색상 충돌이 심하고 스타일이 매칭되지 않아요',
        items: [
          tops[2] || null,
          bottoms[0] || null,
          shoes[2] || null,
        ]
      },
      {
        id: 2,
        title: '워스트 코디 #2',
        reason: '계절감이 맞지 않고 밸런스가 어색해요',
        items: [
          tops[1] || null,
          bottoms[2] || null,
          shoes[0] || null,
        ]
      },
      {
        id: 3,
        title: '워스트 코디 #3',
        reason: '전체적인 톤이 산만하고 조화롭지 않아요',
        items: [
          tops[0] || null,
          bottoms[1] || null,
          shoes[1] || null,
        ]
      }
    ];

    return worstCombinations;
  };

  const bestOutfits = getBestOutfits();
  const worstOutfits = getWorstOutfits();

  return (
    <div className="min-h-screen pb-8">
      {/* Header */}
      <div className="p-6 md:p-8 space-y-1">
        <h1 className="text-2xl md:text-3xl text-white">안녕하세요 👋</h1>
        <p className="text-neutral-400">오늘의 스타일을 추천해드릴게요</p>
      </div>

      {/* AI Diagnosis CTA */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mx-6 md:mx-8 mb-6 md:mb-8"
      >
        <button
          onClick={onStartDiagnosis}
          className="w-full bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-2xl p-6 md:p-8 text-left relative overflow-hidden group"
        >
          <div className="relative z-10">
            <div className="flex items-center gap-2 mb-2">
              <Sparkles className="w-5 h-5 text-white" />
              <span className="text-white">AI 스타일 진단</span>
            </div>
            <p className="text-white/90 text-sm md:text-base mb-4">
              사진 한 장으로 내 체형과 피부톤을 분석하고<br />
              맞춤 스타일을 추천받아보세요
            </p>
            <div className="inline-flex items-center gap-1 bg-white/20 rounded-full px-4 py-2 text-sm text-white">
              <span>진단 시작하기</span>
              <ChevronRight className="w-4 h-4" />
            </div>
          </div>
          <div className="absolute inset-0 bg-gradient-to-r from-transparent to-white/10 opacity-0 group-hover:opacity-100 transition-opacity" />
        </button>
      </motion.div>

      {/* Today's Recommendations */}
      <div className="px-6 md:px-8 space-y-4 md:space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-xl md:text-2xl text-white">오늘의 추천 코디</h2>
          <button className="text-sm text-neutral-400 hover:text-white transition-colors">
            전체보기
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 md:gap-4">
          {outfitRecommendations.map((outfit, index) => (
            <motion.div
              key={outfit.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
              className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 hover:border-neutral-700 transition-colors"
            >
              <div className="flex gap-4">
                {/* Color Swatches */}
                <div className="flex flex-col gap-2">
                  {outfit.colors.map((color, i) => (
                    <div
                      key={i}
                      className={`w-12 h-12 rounded-lg ${color} border border-neutral-700`}
                    />
                  ))}
                </div>

                {/* Content */}
                <div className="flex-1 space-y-2">
                  <h3 className="text-white">{outfit.title}</h3>
                  <div className="space-y-1">
                    {outfit.items.map((item, i) => (
                      <p key={i} className="text-sm text-neutral-400">
                        • {item}
                      </p>
                    ))}
                  </div>
                  <div className="pt-2">
                    <p className="text-xs text-neutral-500 bg-neutral-800/50 rounded-lg px-3 py-2">
                      💡 {outfit.reason}
                    </p>
                  </div>
                </div>

                {/* Arrow */}
                <div className="flex items-center">
                  <ChevronRight className="w-5 h-5 text-neutral-600" />
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Best Outfits from Closet */}
      <div className="px-6 md:px-8 mt-8 md:mt-12 space-y-4">
        <div className="flex items-center gap-2">
          <ThumbsUp className="w-5 h-5 text-green-400" />
          <h2 className="text-xl md:text-2xl text-white">옷장 속 베스트 코디</h2>
        </div>
        <p className="text-sm text-neutral-500">당신의 옷장 아이템으로 만든 최고의 조합이에요</p>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {bestOutfits.map((outfit, index) => (
            <motion.div
              key={outfit.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
              className="bg-neutral-900 border border-green-900/30 rounded-xl p-4 hover:border-green-700/50 transition-colors"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-white">{outfit.title}</h3>
                  <ThumbsUp className="w-4 h-4 text-green-400" />
                </div>

                {/* Item Images */}
                <div className="flex gap-2">
                  {outfit.items.map((item, i) => (
                    <div
                      key={i}
                      className="flex-1 aspect-square bg-neutral-800 rounded-lg border border-neutral-700 overflow-hidden"
                    >
                      {item ? (
                        item.image.startsWith('data:image') ? (
                          <img
                            src={item.image}
                            alt={item.name}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-3xl">
                            {item.image}
                          </div>
                        )
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <div className="w-8 h-8 border-2 border-dashed border-neutral-600 rounded" />
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                {/* Item Names */}
                <div className="space-y-1">
                  {outfit.items.map((item, i) => (
                    item && (
                      <p key={i} className="text-xs text-neutral-400 truncate">
                        • {item.name}
                      </p>
                    )
                  ))}
                </div>

                {/* Reason */}
                <p className="text-xs text-green-400/80 bg-green-950/30 rounded-lg px-3 py-2">
                  {outfit.reason}
                </p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Worst Outfits from Closet */}
      <div className="px-6 md:px-8 mt-8 space-y-4">
        <div className="flex items-center gap-2">
          <ThumbsDown className="w-5 h-5 text-red-400" />
          <h2 className="text-xl md:text-2xl text-white">피해야 할 조합</h2>
        </div>
        <p className="text-sm text-neutral-500">이런 조합은 피하는 게 좋아요</p>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {worstOutfits.map((outfit, index) => (
            <motion.div
              key={outfit.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
              className="bg-neutral-900 border border-red-900/30 rounded-xl p-4 hover:border-red-700/50 transition-colors"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-white">{outfit.title}</h3>
                  <ThumbsDown className="w-4 h-4 text-red-400" />
                </div>

                {/* Item Images */}
                <div className="flex gap-2">
                  {outfit.items.map((item, i) => (
                    <div
                      key={i}
                      className="flex-1 aspect-square bg-neutral-800 rounded-lg border border-neutral-700 overflow-hidden opacity-60"
                    >
                      {item ? (
                        item.image.startsWith('data:image') ? (
                          <img
                            src={item.image}
                            alt={item.name}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-3xl">
                            {item.image}
                          </div>
                        )
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <div className="w-8 h-8 border-2 border-dashed border-neutral-600 rounded" />
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                {/* Item Names */}
                <div className="space-y-1">
                  {outfit.items.map((item, i) => (
                    item && (
                      <p key={i} className="text-xs text-neutral-400 truncate">
                        • {item.name}
                      </p>
                    )
                  ))}
                </div>

                {/* Reason */}
                <p className="text-xs text-red-400/80 bg-red-950/30 rounded-lg px-3 py-2">
                  {outfit.reason}
                </p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
  );
}