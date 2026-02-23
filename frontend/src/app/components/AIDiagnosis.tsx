import React, { useState, useRef } from 'react';
import { Camera, X, CheckCircle2, Palette, Ruler, Sparkles } from 'lucide-react';
import { Button } from './ui/button';
import { motion, AnimatePresence } from 'motion/react';

interface AIDiagnosisProps {
  onComplete: () => void;
}

type DiagnosisStep = 'upload' | 'analyzing' | 'results';

const analysisSteps = [
  { label: '실루엣 추출 중', icon: Camera, duration: 2000 },
  { label: '비율/핏 분석 중', icon: Ruler, duration: 2000 },
  { label: '톤 & 스타일 매칭 중', icon: Palette, duration: 2000 },
];

export default function AIDiagnosis({ onComplete }: AIDiagnosisProps) {
  const [step, setStep] = useState<DiagnosisStep>('upload');
  const [uploadedImage, setUploadedImage] = useState<string | null>(null);
  const [currentAnalysisStep, setCurrentAnalysisStep] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setUploadedImage(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleStartAnalysis = () => {
    setStep('analyzing');
    let stepIndex = 0;
    
    const interval = setInterval(() => {
      stepIndex++;
      setCurrentAnalysisStep(stepIndex);
      
      if (stepIndex >= analysisSteps.length) {
        clearInterval(interval);
        setTimeout(() => {
          setStep('results');
        }, 500);
      }
    }, 2000);
  };

  return (
    <div className="min-h-screen bg-neutral-950">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-neutral-800">
        <h2 className="text-lg text-white">AI 스타일 진단</h2>
        <button onClick={onComplete} className="p-2 hover:bg-neutral-800 rounded-lg transition-colors">
          <X className="w-5 h-5 text-neutral-400" />
        </button>
      </div>

      <AnimatePresence mode="wait">
        {/* Upload Step */}
        {step === 'upload' && (
          <motion.div
            key="upload"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="p-6 space-y-6"
          >
            <div className="text-center space-y-2">
              <h3 className="text-xl text-white">전신 사진을 업로드해주세요</h3>
              <p className="text-sm text-neutral-400">
                정면을 바라보고 찍은 사진이 가장 정확합니다
              </p>
            </div>

            {/* Upload Area */}
            <div className="relative">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleImageUpload}
                className="hidden"
              />
              
              {uploadedImage ? (
                <div className="relative">
                  <img
                    src={uploadedImage}
                    alt="Uploaded"
                    className="w-full h-96 object-cover rounded-2xl"
                  />
                  <button
                    onClick={() => setUploadedImage(null)}
                    className="absolute top-4 right-4 bg-black/50 backdrop-blur-sm p-2 rounded-full"
                  >
                    <X className="w-5 h-5 text-white" />
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="w-full h-96 border-2 border-dashed border-neutral-700 rounded-2xl flex flex-col items-center justify-center gap-4 hover:border-neutral-600 hover:bg-neutral-900/30 transition-all"
                >
                  <div className="w-16 h-16 bg-neutral-800 rounded-full flex items-center justify-center">
                    <Camera className="w-8 h-8 text-neutral-400" />
                  </div>
                  <div className="text-center">
                    <p className="text-white">사진 업로드</p>
                    <p className="text-sm text-neutral-500 mt-1">클릭하여 선택</p>
                  </div>
                </button>
              )}
            </div>

            {/* Tips */}
            <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 space-y-2">
              <p className="text-sm text-white">📸 촬영 팁</p>
              <ul className="text-xs text-neutral-400 space-y-1">
                <li>• 밝은 조명에서 촬영해주세요</li>
                <li>• 전신이 모두 나오도록 해주세요</li>
                <li>• 정면을 바라보고 서주세요</li>
              </ul>
            </div>

            {uploadedImage && (
              <Button
                onClick={handleStartAnalysis}
                className="w-full bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 py-6"
              >
                분석 시작하기
              </Button>
            )}
          </motion.div>
        )}

        {/* Analyzing Step */}
        {step === 'analyzing' && (
          <motion.div
            key="analyzing"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="p-6 flex flex-col items-center justify-center min-h-[calc(100vh-80px)]"
          >
            <div className="w-full max-w-sm space-y-8">
              {analysisSteps.map((stepItem, index) => {
                const Icon = stepItem.icon;
                const isActive = index === currentAnalysisStep;
                const isCompleted = index < currentAnalysisStep;

                return (
                  <motion.div
                    key={index}
                    initial={{ opacity: 0.3 }}
                    animate={{
                      opacity: isActive ? 1 : isCompleted ? 0.6 : 0.3,
                      scale: isActive ? 1.05 : 1,
                    }}
                    className="flex items-center gap-4"
                  >
                    <div
                      className={`w-12 h-12 rounded-full flex items-center justify-center ${
                        isCompleted
                          ? 'bg-green-500'
                          : isActive
                          ? 'bg-gradient-to-r from-blue-500 to-purple-600'
                          : 'bg-neutral-800'
                      }`}
                    >
                      {isCompleted ? (
                        <CheckCircle2 className="w-6 h-6 text-white" />
                      ) : (
                        <Icon className="w-6 h-6 text-white" />
                      )}
                    </div>
                    <div className="flex-1">
                      <p className="text-white">{stepItem.label}</p>
                      {isActive && (
                        <div className="mt-2 h-1 bg-neutral-800 rounded-full overflow-hidden">
                          <motion.div
                            initial={{ width: '0%' }}
                            animate={{ width: '100%' }}
                            transition={{ duration: 2, ease: 'linear' }}
                            className="h-full bg-gradient-to-r from-blue-500 to-purple-600"
                          />
                        </div>
                      )}
                    </div>
                  </motion.div>
                );
              })}
            </div>
          </motion.div>
        )}

        {/* Results Step */}
        {step === 'results' && (
          <motion.div
            key="results"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="p-6 space-y-6 pb-24"
          >
            {/* Success Header */}
            <div className="text-center space-y-3">
              <div className="w-16 h-16 bg-gradient-to-r from-green-500 to-emerald-600 rounded-full flex items-center justify-center mx-auto">
                <Sparkles className="w-8 h-8 text-white" />
              </div>
              <h3 className="text-xl text-white">분석 완료!</h3>
              <p className="text-sm text-neutral-400">맞춤 스타일 결과를 확인하세요</p>
            </div>

            {/* Body Type */}
            <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-5 space-y-4">
              <div className="flex items-center gap-2">
                <Ruler className="w-5 h-5 text-blue-400" />
                <h4 className="text-white">체형 분석</h4>
              </div>
              <div className="space-y-3">
                <div>
                  <p className="text-sm text-neutral-400">체형 타입</p>
                  <p className="text-lg text-white">상체 강조형 (역삼각)</p>
                </div>
                <div className="bg-neutral-800/50 rounded-lg p-3 space-y-2">
                  <p className="text-sm text-green-400">✓ 추천 핏</p>
                  <p className="text-xs text-neutral-400">
                    • 슬림/레귤러 핏으로 균형감 유지<br />
                    • 하의는 약간 여유있는 핏 추천
                  </p>
                </div>
                <div className="bg-neutral-800/50 rounded-lg p-3 space-y-2">
                  <p className="text-sm text-red-400">✗ 피해야 할 핏</p>
                  <p className="text-xs text-neutral-400">
                    • 오버사이즈 상의 (더 넓어 보임)<br />
                    • 스키니 팬츠 (상하 불균형)
                  </p>
                </div>
              </div>
            </div>

            {/* Skin Tone */}
            <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-5 space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Palette className="w-5 h-5 text-purple-400" />
                  <h4 className="text-white">피부톤 분석</h4>
                </div>
                <button className="text-xs text-neutral-400 hover:text-white transition-colors">
                  다시 측정
                </button>
              </div>
              <div className="space-y-3">
                <div>
                  <p className="text-sm text-neutral-400">추정 톤</p>
                  <p className="text-lg text-white">웜톤 (신뢰도 78%)</p>
                  <p className="text-xs text-neutral-500 mt-1">
                    ⚠️ 조명 환경에 따라 오차가 있을 수 있습니다
                  </p>
                </div>
                <div className="flex gap-2">
                  <div className="flex-1 bg-amber-700 rounded-lg h-12" />
                  <div className="flex-1 bg-orange-600 rounded-lg h-12" />
                  <div className="flex-1 bg-red-800 rounded-lg h-12" />
                  <div className="flex-1 bg-yellow-700 rounded-lg h-12" />
                </div>
                <p className="text-xs text-neutral-400">
                  따뜻한 톤의 색상이 잘 어울립니다
                </p>
              </div>
            </div>

            {/* Recommended Outfits */}
            <div className="bg-gradient-to-br from-blue-500/10 to-purple-600/10 border border-blue-500/20 rounded-xl p-5 space-y-3">
              <h4 className="text-white">맞춤 코디 추천</h4>
              <div className="space-y-2">
                <div className="bg-neutral-900/50 rounded-lg p-3">
                  <p className="text-sm text-white mb-1">데일리 캐주얼</p>
                  <p className="text-xs text-neutral-400">
                    베이지 니트 + 블랙 슬랙스 + 브라운 로퍼
                  </p>
                </div>
                <div className="bg-neutral-900/50 rounded-lg p-3">
                  <p className="text-sm text-white mb-1">비즈니스 캐주얼</p>
                  <p className="text-xs text-neutral-400">
                    네이비 블레이저 + 화이트 셔츠 + 그레이 팬츠
                  </p>
                </div>
                <div className="bg-neutral-900/50 rounded-lg p-3">
                  <p className="text-sm text-white mb-1">주말 외출</p>
                  <p className="text-xs text-neutral-400">
                    카키 코치 재킷 + 화이트 티 + 데님 팬츠
                  </p>
                </div>
              </div>
            </div>

            {/* CTA */}
            <Button
              onClick={onComplete}
              className="w-full bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 py-6"
            >
              홈으로 돌아가기
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
