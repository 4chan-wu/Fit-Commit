import React from 'react';
import { Sparkles, Camera, Shirt, TrendingUp } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion } from 'motion/react';

interface MainPageProps {
  onGetStarted: () => void;
}

export default function MainPage({ onGetStarted }: MainPageProps) {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-neutral-950">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="text-center space-y-8 max-w-lg"
      >
        {/* Logo */}
        <motion.div
          initial={{ scale: 0.8 }}
          animate={{ scale: 1 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="flex flex-col items-center gap-4"
        >
          <div className="w-16 h-16 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-2xl flex items-center justify-center">
            <Sparkles className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-4xl md:text-5xl tracking-tight text-white">Fit Commit</h1>
        </motion.div>

        {/* Description */}
        <div className="space-y-3">
          <p className="text-neutral-400 text-lg md:text-xl max-w-md mx-auto">
            사진 한 장으로 체형·피부톤을 분석하고
            <br />
            머리부터 코디까지 자동 추천
          </p>
        </div>

        {/* Features */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.4, duration: 0.6 }}
          className="grid grid-cols-3 gap-4 py-8"
        >
          <div className="flex flex-col items-center gap-3">
            <div className="w-16 h-16 bg-neutral-900 border border-neutral-800 rounded-xl flex items-center justify-center hover:border-neutral-700 transition-colors">
              <Camera className="w-7 h-7 text-blue-400" />
            </div>
            <span className="text-sm text-neutral-400">AI 진단</span>
          </div>
          <div className="flex flex-col items-center gap-3">
            <div className="w-16 h-16 bg-neutral-900 border border-neutral-800 rounded-xl flex items-center justify-center hover:border-neutral-700 transition-colors">
              <Shirt className="w-7 h-7 text-purple-400" />
            </div>
            <span className="text-sm text-neutral-400">스마트 코디</span>
          </div>
          <div className="flex flex-col items-center gap-3">
            <div className="w-16 h-16 bg-neutral-900 border border-neutral-800 rounded-xl flex items-center justify-center hover:border-neutral-700 transition-colors">
              <TrendingUp className="w-7 h-7 text-green-400" />
            </div>
            <span className="text-sm text-neutral-400">취향 학습</span>
          </div>
        </motion.div>

        {/* CTA Button */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6, duration: 0.6 }}
          className="pt-4"
        >
          <Button
            onClick={onGetStarted}
            className="w-full bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 text-white py-6 rounded-xl text-lg"
          >
            시작하기
          </Button>
        </motion.div>
      </motion.div>
    </div>
  );
}