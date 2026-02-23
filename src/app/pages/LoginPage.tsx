import React, { useState } from 'react';
import { MessageCircle, Mail, Lock, User } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion } from 'motion/react';

interface LoginPageProps {
  onLogin: () => void;
}

export default function LoginPage({ onLogin }: LoginPageProps) {
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // 데모용이므로 바로 로그인 처리
    onLogin();
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-sm space-y-8"
      >
        <div className="text-center space-y-2">
          <h2 className="text-2xl text-white">
            {mode === 'login' ? '로그인' : '회원가입'}
          </h2>
          <p className="text-neutral-400">
            {mode === 'login' ? '간편하게 시작하세요' : '새로운 계정을 만드세요'}
          </p>
        </div>

        <div className="space-y-4">
          {/* Email/Password Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === 'signup' && (
              <div className="relative">
                <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-neutral-400" />
                <input
                  type="text"
                  placeholder="이름"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-neutral-900 border border-neutral-800 rounded-xl py-4 pl-12 pr-4 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  required={mode === 'signup'}
                />
              </div>
            )}

            <div className="relative">
              <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-neutral-400" />
              <input
                type="email"
                placeholder="이메일"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full bg-neutral-900 border border-neutral-800 rounded-xl py-4 pl-12 pr-4 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                required
              />
            </div>

            <div className="relative">
              <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-neutral-400" />
              <input
                type="password"
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-neutral-900 border border-neutral-800 rounded-xl py-4 pl-12 pr-4 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                required
              />
            </div>

            <Button
              type="submit"
              className="w-full bg-blue-600 hover:bg-blue-700 text-white py-6 rounded-xl"
            >
              {mode === 'login' ? '로그인' : '회원가입'}
            </Button>
          </form>

          {/* Toggle Mode */}
          <button
            onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}
            className="w-full text-sm text-neutral-400 hover:text-white transition-colors"
          >
            {mode === 'login' ? '계정이 없으신가요? 회원가입' : '이미 계정이 있으신가요? 로그인'}
          </button>

          {/* Divider */}
          <div className="relative py-4">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-neutral-800"></div>
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-4 bg-neutral-950 text-neutral-500">또는</span>
            </div>
          </div>

          {/* Kakao Login */}
          <Button
            onClick={onLogin}
            className="w-full bg-[#FEE500] hover:bg-[#FDD835] text-[#000000] py-6 rounded-xl flex items-center justify-center gap-3"
          >
            <MessageCircle className="w-5 h-5" fill="currentColor" />
            카카오 로그인
          </Button>

          {/* Demo Note */}
          <p className="text-xs text-center text-neutral-500">
            데모 버전입니다. 실제 로그인은 연동되지 않습니다.
          </p>
        </div>
      </motion.div>
    </div>
  );
}