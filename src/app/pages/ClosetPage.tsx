import React, { useState } from 'react';
import { Plus, Search, X, Upload, Tag, Shirt, TrendingUp, Heart, Wand2, Share2, Users, ChevronDown, Folder, Archive, Edit2, Trash2, ArrowLeft, MoreVertical, TrendingDown, Clock, AlertTriangle, Award } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion, AnimatePresence } from 'motion/react';
import { useCloset } from '../context/ClosetContext';
import { ClosetStatistics } from '../components/ClosetStatistics';

const categories = ['전체', '상의', '하의', '아우터', '신발', '악세서리'];
const seasons = ['봄', '여름', '가을', '겨울'];

export default function ClosetPage() {
  const { closets, closetItems, setClosets, setClosetItems } = useCloset();
  const [selectedClosetId, setSelectedClosetId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'list' | 'detail'>('list');
  const [searchQuery, setSearchQuery] = useState('');
  
  // Filters for default closet
  const [filterCategories, setFilterCategories] = useState<string[]>([]);
  const [filterSeasons, setFilterSeasons] = useState<string[]>([]);
  
  // Closet Modal
  const [showClosetModal, setShowClosetModal] = useState(false);
  const [newClosetName, setNewClosetName] = useState('');
  const [newClosetDescription, setNewClosetDescription] = useState('');
  const [newClosetCategories, setNewClosetCategories] = useState<string[]>([]);
  const [newClosetSeasons, setNewClosetSeasons] = useState<string[]>([]);
  
  // Item Modal
  const [showItemModal, setShowItemModal] = useState(false);
  const [uploadedImage, setUploadedImage] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analysisStep, setAnalysisStep] = useState(0);
  const [newItemName, setNewItemName] = useState('');
  const [newItemCategory, setNewItemCategory] = useState('');
  const [newItemSeasons, setNewItemSeasons] = useState<string[]>([]);

  const analysisSteps = [
    { text: '이미지 분석 중...' },
    { text: '카테고리 식별 중...' },
    { text: '계절감 분석 중...' },
    { text: '정보 추출 중...' },
  ];

  const filteredItems = closetItems.filter(item => {
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesSearch;
  });

  const toggleClosetCategory = (category: string) => {
    setNewClosetCategories(prev =>
      prev.includes(category) ? prev.filter(c => c !== category) : [...prev, category]
    );
  };

  const toggleClosetSeason = (season: string) => {
    setNewClosetSeasons(prev =>
      prev.includes(season) ? prev.filter(s => s !== season) : [...prev, season]
    );
  };

  const toggleFilterCategory = (category: string) => {
    setFilterCategories(prev =>
      prev.includes(category) ? prev.filter(c => c !== category) : [...prev, category]
    );
  };

  const toggleFilterSeason = (season: string) => {
    setFilterSeasons(prev =>
      prev.includes(season) ? prev.filter(s => s !== season) : [...prev, season]
    );
  };

  const handleAddCloset = () => {
    if (!newClosetName) return;

    const newCloset = {
      id: `closet-${Date.now()}`,
      name: newClosetName,
      description: newClosetDescription,
      tags: {
        categories: newClosetCategories,
        seasons: newClosetSeasons,
      },
      itemIds: [],
    };

    setClosets([...closets, newCloset]);
    resetClosetModal();
  };

  const resetClosetModal = () => {
    setNewClosetName('');
    setNewClosetDescription('');
    setNewClosetCategories([]);
    setNewClosetSeasons([]);
    setShowClosetModal(false);
  };

  const getClosetItems = (closetId: string) => {
    return closetItems.filter(item => item.closetId === closetId);
  };

  const handleAddItem = () => {
    if (!newItemName || !newItemCategory || newItemSeasons.length === 0 || !uploadedImage) {
      return;
    }

    const itemId = Date.now();

    // Create item for selected closet
    const newItem = {
      id: itemId,
      name: newItemName,
      category: newItemCategory,
      seasons: newItemSeasons,
      image: uploadedImage,
      dateAdded: new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').slice(0, -1),
      closetId: selectedClosetId || '',
    };

    // If not adding to default closet, also create a copy for default closet
    if (selectedClosetId !== 'default-closet') {
      const defaultItem = {
        ...newItem,
        closetId: 'default-closet',
      };
      setClosetItems([newItem, defaultItem, ...closetItems]);
    } else {
      setClosetItems([newItem, ...closetItems]);
    }
    
    // Reset form
    resetModal();
  };

  const getCategoryEmoji = (category: string) => {
    const emojis: Record<string, string> = {
      '상의': '👔',
      '하의': '👖',
      '아우터': '🧥',
      '신발': '👟',
      '악세서리': '👜',
    };
    return emojis[category] || '👕';
  };

  const toggleNewItemSeason = (season: string) => {
    setNewItemSeasons(prev =>
      prev.includes(season) ? prev.filter(s => s !== season) : [...prev, season]
    );
  };

  const handleImageUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onloadend = () => {
      const imageData = reader.result as string;
      setUploadedImage(imageData);
      
      // Start AI analysis simulation
      setIsAnalyzing(true);
      setAnalysisStep(0);
      
      // Simulate analysis steps
      const stepDuration = 800;
      analysisSteps.forEach((_, index) => {
        setTimeout(() => {
          setAnalysisStep(index + 1);
        }, stepDuration * (index + 1));
      });
      
      // Complete analysis and auto-fill
      setTimeout(() => {
        setIsAnalyzing(false);
        autoFillFromImage();
      }, stepDuration * (analysisSteps.length + 1));
    };
    reader.readAsDataURL(file);
  };

  const autoFillFromImage = () => {
    // Simulate AI recommendations (in real app, this would be from backend)
    const randomCategory = categories.filter(c => c !== '전체')[Math.floor(Math.random() * 5)];
    const randomSeasonCount = Math.floor(Math.random() * 3) + 1;
    const randomSeasons = seasons.slice(0, randomSeasonCount);
    
    setNewItemCategory(randomCategory);
    setNewItemSeasons(randomSeasons);
    
    // Auto-generate name based on category
    const itemNames: Record<string, string[]> = {
      '상의': ['셔츠', '니트', '후디', '티셔츠', '블라우스'],
      '하의': ['팬츠', '슬랙스', '청바지', '치마', '반바지'],
      '아우터': ['코트', '자켓', '점퍼', '가디건', '패딩'],
      '신발': ['스니커즈', '로퍼', '부츠', '샌들', '구두'],
      '악세서리': ['가방', '모자', '벨트', '스카프', '시계'],
    };
    
    const itemTypes = itemNames[randomCategory] || ['아이템'];
    const randomItemType = itemTypes[Math.floor(Math.random() * itemTypes.length)];
    setNewItemName(`${randomItemType}`);
  };

  const resetModal = () => {
    setUploadedImage(null);
    setIsAnalyzing(false);
    setAnalysisStep(0);
    setNewItemName('');
    setNewItemCategory('');
    setNewItemSeasons([]);
    setShowItemModal(false);
  };

  const handleClosetClick = (closetId: string) => {
    setSelectedClosetId(closetId);
    setViewMode('detail');
  };

  const handleBackToList = () => {
    setViewMode('list');
    setSelectedClosetId(null);
  };

  const selectedCloset = closets.find(c => c.id === selectedClosetId);

  // List View
  if (viewMode === 'list') {
    return (
      <div className="min-h-screen pb-8">
        {/* Header */}
        <div className="p-6 md:p-8 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl md:text-3xl text-white">내 옷장</h1>
              <p className="text-sm text-neutral-400">{closets.length}개 옷장 · {closetItems.length}개 아이템</p>
            </div>
            <Button 
              onClick={() => setShowClosetModal(true)}
              className="bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600"
            >
              <Plus className="w-4 h-4 mr-2" />
              옷장 추가
            </Button>
          </div>
        </div>

        {/* Search */}
        <div className="px-6 md:px-8 mb-6">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-500" />
            <input
              type="text"
              placeholder="아이템 검색..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-neutral-900 border border-neutral-800 rounded-xl pl-10 pr-4 py-3 text-white placeholder:text-neutral-500 focus:outline-none focus:border-neutral-700"
            />
          </div>
        </div>

        {/* Closets Grid - Compact Cards */}
        <div className="px-6 md:px-8">
          {closets.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {closets.map((closet, closetIndex) => {
                const items = getClosetItems(closet.id);
                
                return (
                  <motion.div
                    key={closet.id}
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: closetIndex * 0.05, duration: 0.3 }}
                    onClick={() => handleClosetClick(closet.id)}
                    className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 hover:border-neutral-700 transition-all cursor-pointer group"
                  >
                    {/* Closet Header */}
                    <div className="mb-4">
                      <div className="flex items-start justify-between mb-2">
                        <h3 className="text-lg text-white group-hover:text-blue-400 transition-colors">{closet.name}</h3>
                        <span className="text-xs text-neutral-500 bg-neutral-800 px-2 py-1 rounded-full">
                          {items.length}개
                        </span>
                      </div>
                      {closet.description && (
                        <p className="text-sm text-neutral-500 line-clamp-2">{closet.description}</p>
                      )}
                    </div>
                    
                    {/* Tags */}
                    <div className="flex flex-wrap gap-1.5 mb-4">
                      {closet.tags.categories.slice(0, 3).map((category) => (
                        <span
                          key={category}
                          className="text-xs px-2 py-0.5 bg-blue-500/10 border border-blue-500/30 rounded-full text-blue-400"
                        >
                          {category}
                        </span>
                      ))}
                      {closet.tags.seasons.slice(0, 2).map((season) => (
                        <span
                          key={season}
                          className="text-xs px-2 py-0.5 bg-purple-500/10 border border-purple-500/30 rounded-full text-purple-400"
                        >
                          {season}
                        </span>
                      ))}
                      {(closet.tags.categories.length > 3 || closet.tags.seasons.length > 2) && (
                        <span className="text-xs px-2 py-0.5 bg-neutral-800 rounded-full text-neutral-500">
                          +{(closet.tags.categories.length > 3 ? closet.tags.categories.length - 3 : 0) + 
                            (closet.tags.seasons.length > 2 ? closet.tags.seasons.length - 2 : 0)}
                        </span>
                      )}
                    </div>

                    {/* Preview Items */}
                    {items.length > 0 ? (
                      <div className="grid grid-cols-4 gap-2">
                        {items.slice(0, 4).map((item) => (
                          <div
                            key={item.id}
                            className="aspect-square bg-neutral-800 rounded-lg overflow-hidden border border-neutral-700"
                          >
                            {item.image.startsWith('data:image') ? (
                              <img
                                src={item.image}
                                alt={item.name}
                                className="w-full h-full object-cover"
                              />
                            ) : (
                              <div className="w-full h-full flex items-center justify-center text-2xl">
                                {item.image}
                              </div>
                            )}
                          </div>
                        ))}
                        {items.length > 4 && Array.from({ length: 4 - Math.min(4, items.length) }).map((_, i) => (
                          <div
                            key={`empty-${i}`}
                            className="aspect-square bg-neutral-800/30 rounded-lg border border-dashed border-neutral-700"
                          />
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-6 border-2 border-dashed border-neutral-800 rounded-xl">
                        <Folder className="w-8 h-8 text-neutral-700 mx-auto mb-1" />
                        <p className="text-xs text-neutral-600">빈 옷장</p>
                      </div>
                    )}
                  </motion.div>
                );
              })}
            </div>
          ) : (
            <div className="text-center py-16 space-y-4">
              <Archive className="w-16 h-16 text-neutral-700 mx-auto" />
              <div>
                <p className="text-neutral-400 text-lg mb-1">옷장이 없습니다</p>
                <p className="text-sm text-neutral-500">첫 번째 옷장을 만들어보세요</p>
              </div>
              <Button
                onClick={() => setShowClosetModal(true)}
                className="bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 mx-auto"
              >
                <Plus className="w-4 h-4 mr-2" />
                옷장 추가
              </Button>
            </div>
          )}
        </div>

        {/* Statistics Section */}
        <ClosetStatistics />

        {/* Modals */}
        {renderModals()}
      </div>
    );
  }

  // Detail View
  if (viewMode === 'detail' && selectedCloset) {
    const items = getClosetItems(selectedCloset.id);
    
    // Apply filters for default closet only
    const filteredDetailItems = selectedCloset.isDefault
      ? items.filter(item => {
          const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase());
          const matchesCategory = filterCategories.length === 0 || filterCategories.includes(item.category);
          const matchesSeasons = filterSeasons.length === 0 || item.seasons.some(s => filterSeasons.includes(s));
          return matchesSearch && matchesCategory && matchesSeasons;
        })
      : items.filter(item =>
          item.name.toLowerCase().includes(searchQuery.toLowerCase())
        );

    return (
      <div className="min-h-screen pb-8">
        {/* Header */}
        <div className="p-6 md:p-8 space-y-4">
          <div className="flex items-center gap-3">
            <button
              onClick={handleBackToList}
              className="p-2 hover:bg-neutral-800 rounded-lg transition-colors"
            >
              <ArrowLeft className="w-5 h-5 text-neutral-400" />
            </button>
            <div className="flex-1">
              <h1 className="text-2xl md:text-3xl text-white">{selectedCloset.name}</h1>
              <p className="text-sm text-neutral-400">{filteredDetailItems.length}개 아이템</p>
            </div>
            <Button 
              onClick={() => setShowItemModal(true)}
              className="bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600"
            >
              <Plus className="w-4 h-4 mr-2" />
              아이템 추가
            </Button>
          </div>

          {/* Description and Tags */}
          {(selectedCloset.description || selectedCloset.tags.categories.length > 0 || selectedCloset.tags.seasons.length > 0) && !selectedCloset.isDefault && (
            <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-3">
              {selectedCloset.description && (
                <p className="text-sm text-neutral-400">{selectedCloset.description}</p>
              )}
              <div className="flex flex-wrap gap-2">
                {selectedCloset.tags.categories.map((category) => (
                  <span
                    key={category}
                    className="text-xs px-3 py-1 bg-blue-500/10 border border-blue-500/30 rounded-full text-blue-400"
                  >
                    {category}
                  </span>
                ))}
                {selectedCloset.tags.seasons.map((season) => (
                  <span
                    key={season}
                    className="text-xs px-3 py-1 bg-purple-500/10 border border-purple-500/30 rounded-full text-purple-400"
                  >
                    {season}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Filters - Only for Default Closet */}
          {selectedCloset.isDefault && (
            <div className="space-y-3">
              {/* Category Filters */}
              <div>
                <p className="text-sm text-neutral-400 mb-2">옷 종류</p>
                <div className="flex flex-wrap gap-2">
                  {categories.filter(c => c !== '전체').map((category) => (
                    <button
                      key={category}
                      onClick={() => toggleFilterCategory(category)}
                      className={`px-4 py-2 rounded-xl border transition-all ${
                        filterCategories.includes(category)
                          ? 'border-blue-500 bg-blue-500/10 text-white'
                          : 'border-neutral-800 bg-neutral-900 text-neutral-400 hover:border-neutral-700'
                      }`}
                    >
                      {category}
                    </button>
                  ))}
                </div>
              </div>

              {/* Season Filters */}
              <div>
                <p className="text-sm text-neutral-400 mb-2">계절</p>
                <div className="flex flex-wrap gap-2">
                  {seasons.map((season) => (
                    <button
                      key={season}
                      onClick={() => toggleFilterSeason(season)}
                      className={`px-4 py-2 rounded-xl border transition-all ${
                        filterSeasons.includes(season)
                          ? 'border-purple-500 bg-purple-500/10 text-white'
                          : 'border-neutral-800 bg-neutral-900 text-neutral-400 hover:border-neutral-700'
                      }`}
                    >
                      {season}
                    </button>
                  ))}
                </div>
              </div>

              {/* Active Filters Count */}
              {(filterCategories.length > 0 || filterSeasons.length > 0) && (
                <div className="flex items-center gap-2">
                  <p className="text-xs text-neutral-500">
                    {filterCategories.length + filterSeasons.length}개 필터 활성화
                  </p>
                  <button
                    onClick={() => {
                      setFilterCategories([]);
                      setFilterSeasons([]);
                    }}
                    className="text-xs text-blue-400 hover:text-blue-300"
                  >
                    모두 지우기
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Search */}
        <div className="px-6 md:px-8 mb-6">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-500" />
            <input
              type="text"
              placeholder="이 옷장에서 검색..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-neutral-900 border border-neutral-800 rounded-xl pl-10 pr-4 py-3 text-white placeholder:text-neutral-500 focus:outline-none focus:border-neutral-700"
            />
          </div>
        </div>

        {/* Items Grid */}
        <div className="px-6 md:px-8">
          {filteredDetailItems.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
              {filteredDetailItems.map((item, index) => (
                <motion.div
                  key={item.id}
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: index * 0.05, duration: 0.2 }}
                  className="group bg-neutral-900 border border-neutral-800 rounded-xl overflow-hidden hover:border-neutral-700 hover:shadow-lg transition-all cursor-pointer"
                >
                  {/* Item Image with Hanger Effect */}
                  <div className="relative">
                    {/* Hanger */}
                    <div className="absolute top-2 left-1/2 -translate-x-1/2 z-10">
                      <div className="w-6 h-0.5 bg-neutral-600 rounded-full" />
                      <div className="w-0.5 h-2 bg-neutral-600 mx-auto" />
                    </div>
                    
                    {/* Item Display */}
                    <div className="h-40 flex items-center justify-center border-b border-neutral-800 relative group-hover:scale-105 transition-transform overflow-hidden bg-neutral-950">
                      {item.image.startsWith('data:image') ? (
                        <img
                          src={item.image}
                          alt={item.name}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <span className="text-4xl">{item.image}</span>
                      )}
                      
                      {/* Seasons Tags Overlay */}
                      <div className="absolute top-2 right-2 flex flex-col gap-1">
                        {item.seasons.slice(0, 2).map((season) => (
                          <span
                            key={season}
                            className="text-xs px-2 py-0.5 bg-black/60 backdrop-blur-sm rounded-full"
                          >
                            {season}
                          </span>
                        ))}
                        {item.seasons.length > 2 && (
                          <span className="text-xs px-2 py-0.5 bg-black/60 backdrop-blur-sm rounded-full">
                            +{item.seasons.length - 2}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Item Details */}
                  <div className="p-3 space-y-1.5">
                    <p className="text-sm text-white font-medium truncate">{item.name}</p>
                    
                    <span className="inline-block text-xs px-2 py-0.5 bg-neutral-800 border border-neutral-700 rounded text-neutral-400">
                      {item.category}
                    </span>
                    
                    <div className="flex flex-wrap gap-1 pt-1">
                      {item.seasons.map((season) => (
                        <span
                          key={season}
                          className="text-xs px-1.5 py-0.5 bg-purple-500/10 rounded text-purple-400"
                        >
                          {season}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Shelf Effect */}
                  <div className="h-1.5 bg-gradient-to-b from-neutral-800 to-neutral-900 border-t border-neutral-700" />
                </motion.div>
              ))}
            </div>
          ) : (
            <div className="text-center py-16 space-y-4">
              <Folder className="w-16 h-16 text-neutral-700 mx-auto" />
              <div>
                <p className="text-neutral-400 text-lg mb-1">
                  {items.length === 0 ? '아직 아이템이 없습니다' : '필터 조건에 맞는 아이템이 없습니다'}
                </p>
                <p className="text-sm text-neutral-500">
                  {items.length === 0 ? '아이템 추가 버튼을 눌러 옷을 추가해보세요' : '다른 필터를 선택해보세요'}
                </p>
              </div>
              {items.length === 0 && (
                <Button
                  onClick={() => setShowItemModal(true)}
                  className="bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 mx-auto"
                >
                  <Plus className="w-4 h-4 mr-2" />
                  아이템 추가
                </Button>
              )}
            </div>
          )}
        </div>

        {/* Modals */}
        {renderModals()}
      </div>
    );
  }

  return null;

  function renderModals() {
    return (
      <>
        {/* Add Item Modal */}
        <AnimatePresence>
          {showItemModal && (
            <motion.div
              className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setShowItemModal(false)}
            >
              <motion.div
                className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 md:p-8 w-full max-w-2xl max-h-[90vh] overflow-y-auto"
                initial={{ scale: 0.9, y: 20 }}
                animate={{ scale: 1, y: 0 }}
                exit={{ scale: 0.9, y: 20 }}
                onClick={(e) => e.stopPropagation()}
              >
                {/* Header */}
                <div className="flex items-center justify-between mb-6">
                  <div>
                    <h2 className="text-2xl text-white mb-1">아이템 추가</h2>
                    <p className="text-sm text-neutral-400">옷장에 새로운 아이템을 추가해보세요</p>
                  </div>
                  <button
                    className="p-2 hover:bg-neutral-800 rounded-lg transition-colors"
                    onClick={() => setShowItemModal(false)}
                  >
                    <X className="w-6 h-6 text-neutral-400" />
                  </button>
                </div>

                <div className="space-y-5">
                  {/* Image Upload */}
                  <div>
                    <label className="text-sm text-neutral-400 mb-2 block">
                      사진 업로드 {uploadedImage && '✓'}
                    </label>
                    
                    {!uploadedImage ? (
                      <label className="block cursor-pointer">
                        <input
                          type="file"
                          accept="image/*"
                          onChange={handleImageUpload}
                          className="hidden"
                        />
                        <div className="border-2 border-dashed border-neutral-700 hover:border-blue-500 rounded-xl p-12 text-center transition-all group">
                          <div className="space-y-3">
                            <div className="w-16 h-16 bg-neutral-800 rounded-full flex items-center justify-center mx-auto group-hover:bg-blue-500/10 transition-colors">
                              <Upload className="w-8 h-8 text-neutral-500 group-hover:text-blue-400 transition-colors" />
                            </div>
                            <div>
                              <p className="text-white mb-1">사진을 업로드해주세요</p>
                              <p className="text-xs text-neutral-500">
                                AI가 자동으로 아이템을 분석합니다
                              </p>
                            </div>
                          </div>
                        </div>
                      </label>
                    ) : (
                      <div className="relative">
                        {isAnalyzing ? (
                          // AI Analysis Loading
                          <div className="bg-neutral-800 border border-neutral-700 rounded-xl p-8 text-center space-y-4">
                            <motion.div
                              initial={{ scale: 0 }}
                              animate={{ scale: 1 }}
                              className="w-20 h-20 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-full flex items-center justify-center mx-auto"
                            >
                              <motion.div
                                animate={{ rotate: 360 }}
                                transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                                className="w-10 h-10 border-4 border-white border-t-transparent rounded-full"
                              />
                            </motion.div>
                            
                            <div className="space-y-2">
                              {analysisSteps.map((step, index) => (
                                <motion.div
                                  key={index}
                                  initial={{ opacity: 0, x: -20 }}
                                  animate={{
                                    opacity: index <= analysisStep ? 1 : 0.3,
                                    x: 0,
                                  }}
                                  transition={{ delay: index * 0.8 }}
                                  className={`flex items-center justify-center gap-2 ${
                                    index <= analysisStep ? 'text-white' : 'text-neutral-600'
                                  }`}
                                >
                                  <span className="text-sm">{step.text}</span>
                                  {index < analysisStep && (
                                    <motion.span
                                      initial={{ scale: 0 }}
                                      animate={{ scale: 1 }}
                                      className="text-green-400"
                                    >
                                      ✓
                                    </motion.span>
                                  )}
                                </motion.div>
                              ))}
                            </div>
                          </div>
                        ) : (
                          // Uploaded Image Preview
                          <div className="space-y-3">
                            <div className="relative rounded-xl overflow-hidden border-2 border-green-500/50">
                              <img
                                src={uploadedImage}
                                alt="Uploaded item"
                                className="w-full h-64 object-contain bg-neutral-800"
                              />
                              <div className="absolute top-2 right-2">
                                <button
                                  onClick={() => {
                                    setUploadedImage(null);
                                    setNewItemName('');
                                    setNewItemCategory('');
                                    setNewItemSeasons([]);
                                  }}
                                  className="p-2 bg-red-500 hover:bg-red-600 rounded-lg transition-colors"
                                >
                                  <X className="w-4 h-4 text-white" />
                                </button>
                              </div>
                            </div>
                            <motion.div
                              initial={{ opacity: 0, y: 10 }}
                              animate={{ opacity: 1, y: 0 }}
                              className="bg-green-500/10 border border-green-500/30 rounded-lg p-3 text-center"
                            >
                              <p className="text-sm text-green-400">
                                ✓ AI 분석 완료! 아래 정보를 확인하고 수정해주세요
                              </p>
                            </motion.div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Show form fields only after image is uploaded and analyzed */}
                  {uploadedImage && !isAnalyzing && (
                    <motion.div
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      className="space-y-5"
                    >
                      {/* Item Name */}
                      <div>
                        <label className="text-sm text-neutral-400 mb-2 block">아이템 이름</label>
                        <div className="relative">
                          <Shirt className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-neutral-500" />
                          <input
                            type="text"
                            placeholder="예: 화이트 옥스포드 셔츠"
                            value={newItemName}
                            onChange={(e) => setNewItemName(e.target.value)}
                            className="w-full bg-neutral-800 border border-neutral-700 rounded-xl pl-12 pr-4 py-3 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </div>
                      </div>

                      {/* Category */}
                      <div>
                        <label className="text-sm text-neutral-400 mb-2 block">카테고리</label>
                        <div className="grid grid-cols-3 gap-2">
                          {categories.filter(c => c !== '전체').map((category) => (
                            <button
                              key={category}
                              onClick={() => setNewItemCategory(category)}
                              className={`p-3 rounded-xl border transition-all ${
                                newItemCategory === category
                                  ? 'border-blue-500 bg-blue-500/10 text-white'
                                  : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                              }`}
                            >
                              {category}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Seasons */}
                      <div>
                        <label className="text-sm text-neutral-400 mb-2 block">계절 (여러 개 선택 가능)</label>
                        <div className="grid grid-cols-4 gap-2">
                          {seasons.map((season) => (
                            <button
                              key={season}
                              onClick={() => toggleNewItemSeason(season)}
                              className={`p-4 rounded-xl border transition-all ${
                                newItemSeasons.includes(season)
                                  ? 'border-purple-500 bg-purple-500/10 text-white'
                                  : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                              }`}
                            >
                              {season}
                            </button>
                          ))}
                        </div>
                      </div>
                    </motion.div>
                  )}
                </div>

                {/* Action Buttons */}
                <div className="flex gap-3 mt-8">
                  <Button
                    onClick={resetModal}
                    variant="outline"
                    className="flex-1 border-neutral-700 text-neutral-300 hover:bg-neutral-800 py-6"
                  >
                    취소
                  </Button>
                  <Button
                    onClick={handleAddItem}
                    disabled={!uploadedImage || isAnalyzing || !newItemName || !newItemCategory || newItemSeasons.length === 0}
                    className={`flex-1 py-6 ${
                      uploadedImage && !isAnalyzing && newItemName && newItemCategory && newItemSeasons.length > 0
                        ? 'bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 text-white'
                        : 'bg-neutral-800 text-neutral-600 cursor-not-allowed'
                    }`}
                  >
                    <Plus className="w-4 h-4 mr-2" />
                    추가하기
                  </Button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Add Closet Modal */}
        <AnimatePresence>
          {showClosetModal && (
            <motion.div
              className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setShowClosetModal(false)}
            >
              <motion.div
                className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 md:p-8 w-full max-w-2xl max-h-[90vh] overflow-y-auto"
                initial={{ scale: 0.9, y: 20 }}
                animate={{ scale: 1, y: 0 }}
                exit={{ scale: 0.9, y: 20 }}
                onClick={(e) => e.stopPropagation()}
              >
                {/* Header */}
                <div className="flex items-center justify-between mb-6">
                  <div>
                    <h2 className="text-2xl text-white mb-1">옷장 추가</h2>
                    <p className="text-sm text-neutral-400">새로운 옷장을 생성해보세요</p>
                  </div>
                  <button
                    className="p-2 hover:bg-neutral-800 rounded-lg transition-colors"
                    onClick={() => setShowClosetModal(false)}
                  >
                    <X className="w-6 h-6 text-neutral-400" />
                  </button>
                </div>

                <div className="space-y-5">
                  {/* Closet Name */}
                  <div>
                    <label className="text-sm text-neutral-400 mb-2 block">옷장 이름</label>
                    <input
                      type="text"
                      placeholder="예: 가을 옷장"
                      value={newClosetName}
                      onChange={(e) => setNewClosetName(e.target.value)}
                      className="w-full bg-neutral-800 border border-neutral-700 rounded-xl pl-4 pr-4 py-3 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>

                  {/* Description */}
                  <div>
                    <label className="text-sm text-neutral-400 mb-2 block">설명</label>
                    <textarea
                      placeholder="옷장에 한 설명을 입력하세요"
                      value={newClosetDescription}
                      onChange={(e) => setNewClosetDescription(e.target.value)}
                      className="w-full bg-neutral-800 border border-neutral-700 rounded-xl pl-4 pr-4 py-3 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>

                  {/* Categories */}
                  <div>
                    <label className="text-sm text-neutral-400 mb-2 block">카테고리 (여러 개 선택 가능)</label>
                    <div className="grid grid-cols-3 gap-2">
                      {categories.filter(c => c !== '전체').map((category) => (
                        <button
                          key={category}
                          onClick={() => toggleClosetCategory(category)}
                          className={`p-3 rounded-xl border transition-all ${
                            newClosetCategories.includes(category)
                              ? 'border-blue-500 bg-blue-500/10 text-white'
                              : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                          }`}
                        >
                          {category}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Seasons */}
                  <div>
                    <label className="text-sm text-neutral-400 mb-2 block">계절 (여러 개 선택 가능)</label>
                    <div className="grid grid-cols-4 gap-2">
                      {seasons.map((season) => (
                        <button
                          key={season}
                          onClick={() => toggleClosetSeason(season)}
                          className={`p-4 rounded-xl border transition-all ${
                            newClosetSeasons.includes(season)
                              ? 'border-purple-500 bg-purple-500/10 text-white'
                              : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                          }`}
                        >
                          {season}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="flex gap-3 mt-8">
                  <Button
                    onClick={resetClosetModal}
                    variant="outline"
                    className="flex-1 border-neutral-700 text-neutral-300 hover:bg-neutral-800 py-6"
                  >
                    취소
                  </Button>
                  <Button
                    onClick={handleAddCloset}
                    disabled={!newClosetName}
                    className={`flex-1 py-6 ${
                      newClosetName
                        ? 'bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 text-white'
                        : 'bg-neutral-800 text-neutral-600 cursor-not-allowed'
                    }`}
                  >
                    <Plus className="w-4 h-4 mr-2" />
                    추가하기
                  </Button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </>
    );
  }
}