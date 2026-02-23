import React, { useState } from 'react';
import { User, Settings, LogOut, ChevronRight, Sparkles, Check, X, BarChart3, FileText, Bell, ShoppingBag, Calendar as CalendarIcon, Tag, Thermometer, DollarSign, Globe, HelpCircle, Megaphone, MessageCircle, Instagram, Shield, UserCog, Trash2, TrendingUp } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion, AnimatePresence } from 'motion/react';

const genders = ['여자', '남자', '지정되지 않음', '밝히지 않음'];
const lifestyles = ['중고등학생', '대학교 학생', '캐주얼 복장 근무자', '정장 근무자', '유니폼 근무자', '전업주부', '기타'];
const brandTiers = [
  { id: 'budget', label: '저가 브랜드' },
  { id: 'mid', label: '중가 브랜드' },
  { id: 'premium', label: '프리미엄 브랜드' },
  { id: 'luxury', label: '명품 브랜드' },
];

export default function MyPage() {
  const [showProfileSetup, setShowProfileSetup] = useState(false);
  const [profileStep, setProfileStep] = useState(1);
  const [selectedGender, setSelectedGender] = useState<string>('');
  const [selectedLifestyle, setSelectedLifestyle] = useState<string>('');
  const [selectedBrandTier, setSelectedBrandTier] = useState<string>('');
  const [profileCompleted, setProfileCompleted] = useState(false);

  const handleNextStep = () => {
    if (profileStep < 3) {
      setProfileStep(profileStep + 1);
    } else {
      // Complete profile setup
      setProfileCompleted(true);
      setShowProfileSetup(false);
      setProfileStep(1);
    }
  };

  const handlePreviousStep = () => {
    if (profileStep > 1) {
      setProfileStep(profileStep - 1);
    }
  };

  const canProceed = () => {
    if (profileStep === 1) return selectedGender !== '';
    if (profileStep === 2) return selectedLifestyle !== '';
    if (profileStep === 3) return selectedBrandTier !== '';
    return false;
  };

  return (
    <div className="min-h-screen pb-8">
      {/* Header */}
      <div className="p-6 md:p-8 space-y-6">
        <h1 className="text-2xl md:text-3xl text-white">마이 페이지</h1>

        {/* Profile Info Banner - Show if not completed */}
        {!profileCompleted && (
          <motion.button
            onClick={() => setShowProfileSetup(true)}
            className="w-full bg-gradient-to-br from-blue-500/20 via-purple-600/20 to-pink-500/20 border border-blue-500/30 rounded-xl p-5 text-left hover:border-blue-500/50 transition-all group"
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            whileHover={{ scale: 1.01 }}
          >
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-xl flex items-center justify-center flex-shrink-0">
                <Sparkles className="w-6 h-6 text-white" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="text-white font-medium">더 정확한 패션 진단을 위해 정보 입력하기</h3>
                  <ChevronRight className="w-5 h-5 text-blue-400 group-hover:translate-x-1 transition-transform" />
                </div>
                <p className="text-sm text-neutral-400">
                  성별, 라이프스타일, 선호 브랜드를 알려주세요
                </p>
              </div>
            </div>
          </motion.button>
        )}

        {/* User Profile */}
        <div className="flex items-center gap-4">
          <div className="w-20 h-20 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-full flex items-center justify-center">
            <User className="w-10 h-10 text-white" />
          </div>
          <div className="flex-1">
            <h2 className="text-xl text-white mb-1">김유저</h2>
            <p className="text-sm text-neutral-400">fitcommit_user</p>
          </div>
          <button className="p-2 hover:bg-neutral-800 rounded-lg transition-colors">
            <Settings className="w-5 h-5 text-neutral-400" />
          </button>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-2 gap-4">
          {/* Style Stats Card */}
          <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 hover:border-neutral-700 transition-colors cursor-pointer">
            <div className="flex flex-col items-center justify-center space-y-2">
              <div className="w-12 h-12 bg-blue-500/10 rounded-xl flex items-center justify-center">
                <BarChart3 className="w-6 h-6 text-blue-400" />
              </div>
              <p className="text-sm text-white">스타일 통계</p>
            </div>
          </div>

          {/* Monthly Report Card */}
          <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 hover:border-neutral-700 transition-colors cursor-pointer">
            <div className="flex flex-col items-center justify-center space-y-2">
              <div className="w-12 h-12 bg-purple-500/10 rounded-xl flex items-center justify-center">
                <TrendingUp className="w-6 h-6 text-purple-400" />
              </div>
              <p className="text-sm text-white">월간 보고서</p>
              <p className="text-xs text-neutral-500">2025년 12월</p>
              <p className="text-xs text-neutral-500">업데이트</p>
            </div>
          </div>
        </div>
      </div>

      {/* My Style DNA */}
      <div className="px-6 md:px-8 mb-6">
        <div className="bg-gradient-to-br from-blue-500/10 to-purple-600/10 border border-blue-500/20 rounded-xl p-5 space-y-3">
          <h3 className="text-white">내 스타일 DNA</h3>
          <div className="flex flex-wrap gap-2">
            <span className="px-3 py-1 bg-neutral-900/50 border border-neutral-700 rounded-full text-sm text-neutral-300">
              미니멀
            </span>
            <span className="px-3 py-1 bg-neutral-900/50 border border-neutral-700 rounded-full text-sm text-neutral-300">
              베이직
            </span>
            <span className="px-3 py-1 bg-neutral-900/50 border border-neutral-700 rounded-full text-sm text-neutral-300">
              모던
            </span>
          </div>
        </div>
      </div>

      {/* User Settings Section */}
      <div className="px-6 md:px-8 mb-6">
        <h3 className="text-sm text-neutral-500 mb-3">사용자 설정</h3>
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl divide-y divide-neutral-800">
          <SettingItem icon={<User className="w-5 h-5 text-neutral-400" />} label="내 정보" />
          <SettingItem icon={<Sparkles className="w-5 h-5 text-neutral-400" />} label="코디 추천 설정" />
          <SettingItem icon={<Bell className="w-5 h-5 text-neutral-400" />} label="알림" />
          <SettingItem icon={<MessageCircle className="w-5 h-5 text-neutral-400" />} label="패트리 채팅 기록" />
          <SettingItem 
            icon={<Globe className="w-5 h-5 text-neutral-400" />} 
            label="국가" 
            value="대한민국"
            valueColor="text-blue-400"
          />
        </div>
      </div>

      {/* Customer Support Section */}
      <div className="px-6 md:px-8 mb-6">
        <h3 className="text-sm text-neutral-500 mb-3">고객지원</h3>
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl divide-y divide-neutral-800">
          <SettingItem icon={<HelpCircle className="w-5 h-5 text-neutral-400" />} label="FAQ" />
          <SettingItem icon={<Megaphone className="w-5 h-5 text-neutral-400" />} label="공지사항" />
          <SettingItem icon={<MessageCircle className="w-5 h-5 text-neutral-400" />} label="피드백 & 제안" />
        </div>
      </div>

      {/* Service Terms Section */}
      <div className="px-6 md:px-8 mb-6">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm text-neutral-500">서비스 이용약관</h3>
          <span className="text-xs text-neutral-600">Ver 6.13.0</span>
        </div>
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl divide-y divide-neutral-800">
          <SettingItem icon={<FileText className="w-5 h-5 text-neutral-400" />} label="서비스 이용약관" />
          <SettingItem icon={<Shield className="w-5 h-5 text-neutral-400" />} label="개인정보처리방침" />
        </div>
      </div>

      {/* Login Section */}
      <div className="px-6 md:px-8 mb-6">
        <h3 className="text-sm text-neutral-500 mb-3">로그인</h3>
        <div className="bg-neutral-900 border border-neutral-800 rounded-xl divide-y divide-neutral-800">
          <SettingItem 
            icon={<UserCog className="w-5 h-5 text-neutral-400" />} 
            label="로그인 방식 변경" 
          />
          <SettingItem icon={<LogOut className="w-5 h-5 text-neutral-400" />} label="로그아웃" />
          <SettingItem icon={<Trash2 className="w-5 h-5 text-red-400" />} label="회원 탈퇴" labelColor="text-red-400" />
        </div>
      </div>

      {/* Profile Setup Modal */}
      <AnimatePresence>
        {showProfileSetup && (
          <motion.div
            className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setShowProfileSetup(false)}
          >
            <motion.div
              className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 md:p-8 max-w-2xl w-full max-h-[90vh] overflow-y-auto"
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.9, y: 20 }}
              onClick={(e) => e.stopPropagation()}
            >
              {/* Header */}
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-2xl text-white mb-1">프로필 설정</h3>
                  <p className="text-sm text-neutral-400">{profileStep}/3 단계</p>
                </div>
                <button
                  className="p-2 hover:bg-neutral-800 rounded-lg transition-colors"
                  onClick={() => setShowProfileSetup(false)}
                >
                  <X className="w-6 h-6 text-neutral-400" />
                </button>
              </div>

              {/* Progress Bar */}
              <div className="mb-8">
                <div className="h-2 bg-neutral-800 rounded-full overflow-hidden">
                  <motion.div
                    initial={{ width: `${((profileStep - 1) / 3) * 100}%` }}
                    animate={{ width: `${(profileStep / 3) * 100}%` }}
                    transition={{ duration: 0.3 }}
                    className="h-full bg-gradient-to-r from-blue-500 via-purple-600 to-pink-500"
                  />
                </div>
              </div>

              {/* Step Content */}
              <div className="mb-8">
                <AnimatePresence mode="wait">
                  {profileStep === 1 && (
                    <motion.div
                      key="step1"
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -20 }}
                      transition={{ duration: 0.2 }}
                      className="space-y-4"
                    >
                      <h4 className="text-xl text-white mb-4">성별이 무엇인가요?</h4>
                      <div className="grid grid-cols-2 gap-3">
                        {genders.map((gender) => (
                          <button
                            key={gender}
                            className={`p-5 rounded-xl border transition-all text-left ${
                              selectedGender === gender
                                ? 'border-blue-500 bg-blue-500/10 text-white'
                                : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                            }`}
                            onClick={() => setSelectedGender(gender)}
                          >
                            <div className="flex items-center justify-between">
                              <span>{gender}</span>
                              {selectedGender === gender && (
                                <Check className="w-5 h-5 text-blue-400" />
                              )}
                            </div>
                          </button>
                        ))}
                      </div>
                    </motion.div>
                  )}

                  {profileStep === 2 && (
                    <motion.div
                      key="step2"
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -20 }}
                      transition={{ duration: 0.2 }}
                      className="space-y-4"
                    >
                      <h4 className="text-xl text-white mb-4">
                        주된 생활 및 근무 유형을<br />선택해주세요
                      </h4>
                      <div className="grid grid-cols-2 gap-3">
                        {lifestyles.map((lifestyle) => (
                          <button
                            key={lifestyle}
                            className={`p-5 rounded-xl border transition-all text-left ${
                              selectedLifestyle === lifestyle
                                ? 'border-purple-500 bg-purple-500/10 text-white'
                                : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                            }`}
                            onClick={() => setSelectedLifestyle(lifestyle)}
                          >
                            <div className="flex items-center justify-between">
                              <span>{lifestyle}</span>
                              {selectedLifestyle === lifestyle && (
                                <Check className="w-5 h-5 text-purple-400" />
                              )}
                            </div>
                          </button>
                        ))}
                      </div>
                    </motion.div>
                  )}

                  {profileStep === 3 && (
                    <motion.div
                      key="step3"
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -20 }}
                      transition={{ duration: 0.2 }}
                      className="space-y-4"
                    >
                      <h4 className="text-xl text-white mb-4">
                        주로 어떤 가격대의<br />브랜드를 선호하세요?
                      </h4>
                      <div className="grid grid-cols-2 gap-3">
                        {brandTiers.map((tier) => (
                          <button
                            key={tier.id}
                            className={`p-5 rounded-xl border transition-all ${
                              selectedBrandTier === tier.id
                                ? 'border-pink-500 bg-pink-500/10 text-white'
                                : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                            }`}
                            onClick={() => setSelectedBrandTier(tier.id)}
                          >
                            <div className="flex items-center justify-between">
                              <span>{tier.label}</span>
                              {selectedBrandTier === tier.id && (
                                <Check className="w-5 h-5 text-pink-400" />
                              )}
                            </div>
                          </button>
                        ))}
                        <button
                          className={`p-5 rounded-xl border transition-all col-span-2 ${
                            selectedBrandTier === 'unknown'
                              ? 'border-pink-500 bg-pink-500/10 text-white'
                              : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                          }`}
                          onClick={() => setSelectedBrandTier('unknown')}
                        >
                          <div className="flex items-center justify-between">
                            <span>잘 모르겠어요</span>
                            {selectedBrandTier === 'unknown' && (
                              <Check className="w-5 h-5 text-pink-400" />
                            )}
                          </div>
                        </button>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {/* Navigation Buttons */}
              <div className="flex gap-3">
                {profileStep > 1 && (
                  <Button
                    onClick={handlePreviousStep}
                    variant="outline"
                    className="flex-1 border-neutral-700 text-neutral-300 hover:bg-neutral-800 py-6"
                  >
                    이전
                  </Button>
                )}
                <Button
                  onClick={handleNextStep}
                  disabled={!canProceed()}
                  className={`flex-1 py-6 ${
                    canProceed()
                      ? 'bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 text-white'
                      : 'bg-neutral-800 text-neutral-600 cursor-not-allowed'
                  }`}
                >
                  {profileStep === 3 ? '완료' : '다음'}
                </Button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// Setting Item Component
interface SettingItemProps {
  icon: React.ReactNode;
  label: string;
  value?: string;
  valueColor?: string;
  labelColor?: string;
  iconBadge?: React.ReactNode;
}

function SettingItem({ icon, label, value, valueColor = 'text-blue-400', labelColor = 'text-white', iconBadge }: SettingItemProps) {
  return (
    <button className="w-full p-4 text-left hover:bg-neutral-800/50 transition-colors flex items-center justify-between group">
      <div className="flex items-center gap-3">
        {icon}
        <span className={`text-sm ${labelColor}`}>{label}</span>
      </div>
      <div className="flex items-center gap-2">
        {iconBadge && iconBadge}
        {value && <span className={`text-sm ${valueColor}`}>{value}</span>}
        <ChevronRight className="w-5 h-5 text-neutral-600 group-hover:text-neutral-500 transition-colors" />
      </div>
    </button>
  );
}