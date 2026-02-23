import React, { useState } from 'react';
import { Check } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion } from 'motion/react';

interface ProfileSetupProps {
  onComplete: () => void;
}

const genders = ['여자', '남자', '지정되지 않음', '밝히지 않음'];
const lifestyles = ['중고등학생', '대학교 학생', '캐주얼 복장 근무자', '정장 근무자', '유니폼 근무자', '전업주부', '기타'];

const brandTiers = [
  {
    id: 'budget',
    label: '저가 브랜드'
  },
  {
    id: 'mid',
    label: '중가 브랜드'
  },
  {
    id: 'premium',
    label: '프리미엄 브랜드'
  },
  {
    id: 'luxury',
    label: '명품 브랜드'
  }
];

export default function ProfileSetup({ onComplete }: ProfileSetupProps) {
  const [step, setStep] = useState(1);
  const [selectedGender, setSelectedGender] = useState<string>('');
  const [selectedLifestyle, setSelectedLifestyle] = useState<string>('');
  const [selectedBrandTier, setSelectedBrandTier] = useState<string>('');

  const handleNext = () => {
    if (step < 3) {
      setStep(step + 1);
    } else {
      onComplete();
    }
  };

  const canProceed = () => {
    if (step === 1) return selectedGender !== '';
    if (step === 2) return selectedLifestyle !== '';
    if (step === 3) return selectedBrandTier !== '';
    return false;
  };

  return (
    <div className="min-h-screen p-6">
      {/* Progress Bar */}
      <div className="mb-8 space-y-2">
        <div className="flex justify-between text-sm text-neutral-400">
          <span>프로필 설정</span>
          <span>{step}/3</span>
        </div>
        <div className="h-1 bg-neutral-800 rounded-full overflow-hidden">
          <motion.div
            initial={{ width: '33%' }}
            animate={{ width: `${(step / 3) * 100}%` }}
            transition={{ duration: 0.3 }}
            className="h-full bg-gradient-to-r from-blue-500 to-purple-600"
          />
        </div>
      </div>

      {/* Step 1: Gender */}
      {step === 1 && (
        <motion.div
          key="step1"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          className="space-y-6"
        >
          <div className="space-y-2">
            <h2 className="text-2xl text-white">성별이 무엇인가요?</h2>
          </div>

          <div className="space-y-3">
            {genders.map((gender) => (
              <button
                key={gender}
                onClick={() => setSelectedGender(gender)}
                className={`w-full p-5 rounded-xl border transition-all text-left ${
                  selectedGender === gender
                    ? 'border-blue-500 bg-blue-500/10 text-white'
                    : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                }`}
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

      {/* Step 2: Lifestyle */}
      {step === 2 && (
        <motion.div
          key="step2"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          className="space-y-6"
        >
          <div className="space-y-2">
            <h2 className="text-2xl text-white">주된 생활 및 근무 유형을<br />선택해주세요</h2>
          </div>

          <div className="space-y-3">
            {lifestyles.map((lifestyle) => (
              <button
                key={lifestyle}
                onClick={() => setSelectedLifestyle(lifestyle)}
                className={`w-full p-5 rounded-xl border transition-all text-left ${
                  selectedLifestyle === lifestyle
                    ? 'border-purple-500 bg-purple-500/10 text-white'
                    : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
                }`}
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

      {/* Step 3: Brand Preference */}
      {step === 3 && (
        <motion.div
          key="step3"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          className="space-y-6"
        >
          <div className="space-y-2">
            <h2 className="text-2xl text-white">주로 어떤 가격대의<br />브랜드를 선호하세요?</h2>
          </div>

          <div className="space-y-3">
            {brandTiers.map((tier) => (
              <button
                key={tier.id}
                onClick={() => setSelectedBrandTier(tier.id)}
                className={`w-full p-5 rounded-xl border transition-all ${
                  selectedBrandTier === tier.id
                    ? 'border-blue-500 bg-blue-500/10'
                    : 'border-neutral-700 bg-neutral-800/30 hover:border-neutral-600'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className={selectedBrandTier === tier.id ? 'text-white' : 'text-neutral-400'}>
                    {tier.label}
                  </span>
                  {selectedBrandTier === tier.id && (
                    <Check className="w-5 h-5 text-blue-400" />
                  )}
                </div>
              </button>
            ))}

            {/* 잘 모르겠어요 */}
            <button
              onClick={() => setSelectedBrandTier('unknown')}
              className={`w-full p-5 rounded-xl border transition-all ${
                selectedBrandTier === 'unknown'
                  ? 'border-blue-500 bg-blue-500/10 text-white'
                  : 'border-neutral-700 bg-neutral-800/30 text-neutral-400 hover:border-neutral-600'
              }`}
            >
              <div className="flex items-center justify-between">
                <span>잘 모르겠어요</span>
                {selectedBrandTier === 'unknown' && (
                  <Check className="w-5 h-5 text-blue-400" />
                )}
              </div>
            </button>
          </div>
        </motion.div>
      )}

      {/* Navigation Buttons */}
      <div className="fixed bottom-0 left-0 right-0 p-6 bg-neutral-950 max-w-md mx-auto">
        <div className="flex gap-3">
          {step > 1 && (
            <Button
              onClick={() => setStep(step - 1)}
              variant="outline"
              className="flex-1 border-neutral-700 text-neutral-300 hover:bg-neutral-800"
            >
              이전
            </Button>
          )}
          <Button
            onClick={handleNext}
            disabled={!canProceed()}
            className={`flex-1 ${
              canProceed()
                ? 'bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700'
                : 'bg-neutral-800 text-neutral-600'
            }`}
          >
            {step === 3 ? '완료' : '다음'}
          </Button>
        </div>
      </div>
    </div>
  );
}