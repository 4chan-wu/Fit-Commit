import React, { useState } from 'react';
import { ArrowLeft, Menu, Send, Lightbulb, Bot, Sparkles, Trophy, Target, ChevronRight } from 'lucide-react';
import { Button } from '../components/ui/button';
import { motion } from 'motion/react';

const suggestedQuestions = [
  { id: 1, text: '나만의 무드나 감성을 알고 싶어요', icon: '💭' },
  { id: 2, text: '내 체형에 어울리는 핏은 뭘까?', icon: '👔' },
  { id: 3, text: '나와 잘 어울리는 색상은?', icon: '🎨' },
];

const fashionTips = [
  { 
    id: 1, 
    icon: '💡', 
    title: '기본 아이템 활용법',
    content: '화이트 티셔츠, 블랙 진, 네이비 재킷은 모든 코디의 기본이에요. 이 3가지만 있어도 무한한 조합이 가능합니다.'
  },
  { 
    id: 2, 
    icon: '🎨', 
    title: '색상 매칭 법칙',
    content: '같은 톤의 색상을 3가지 이상 사용하면 코디가 산만해져요. 메인 컬러 1개, 서브 컬러 1개, 포인트 컬러 1개면 충분합니다.'
  },
  { 
    id: 3, 
    icon: '👔', 
    title: '핏의 중요성',
    content: '비싼 옷보다 내 몸에 맞는 옷이 더 중요해요. 어깨선과 기장만 맞아도 옷이 훨씬 고급스러워 보입니다.'
  },
  { 
    id: 4, 
    icon: '👟', 
    title: '신발이 결정한다',
    content: '같은 옷이라도 어떤 신발을 신느냐에 따라 완전히 다른 분위기를 연출할 수 있어요. TPO에 맞는 신발 선택이 중요합니다.'
  },
  { 
    id: 5, 
    icon: '🔄', 
    title: '계절별 레이어링',
    content: '겨울엔 얇은 옷을 여러 겹 입는 게 보온도 좋고 스타일리시해요. 봄/가을엔 가디건이나 셔츠를 활용해보세요.'
  },
  { 
    id: 6, 
    icon: '✨', 
    title: '악세사리 포인트',
    content: '심플한 코디에 시계나 반지 하나만 추가해도 세련된 느낌을 줄 수 있어요. 과하지 않게 1-2개가 적당합니다.'
  },
];

interface Message {
  id: number;
  type: 'user' | 'assistant';
  text: string;
  timestamp: Date;
}

export default function StyleChatPage() {
  const [showIntro, setShowIntro] = useState(true);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputText, setInputText] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [randomTip] = useState(() => fashionTips[Math.floor(Math.random() * fashionTips.length)]);

  const sendMessage = async (text: string) => {
  if (!text.trim()) return;

  const userMessage: Message = {
    id: Date.now(),
    type: 'user',
    text: text.trim(),
    timestamp: new Date(),
  };

  // UI에 먼저 userMessage 반영
  setMessages((prev) => [...prev, userMessage]);
  setInputText('');
  setIsTyping(true);

  try {
    // ✅ 주의: messages는 state라서 최신이 아닐 수 있음
    // 안전하게 payload는 "이전 메시지 + 방금 메시지"로 구성
    const payloadMessages = [...messages, userMessage].map((m) => ({
      role: m.type === 'user' ? 'user' : 'assistant',
      content: m.text,
    }));

    const res = await fetch('http://localhost:8000/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages: payloadMessages }),
    });

    if (!res.ok) throw new Error(`API Error: ${res.status}`);

    const data: { reply: string } = await res.json();

    const assistantMessage: Message = {
      id: Date.now() + 1,
      type: 'assistant',
      text: data.reply,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, assistantMessage]);
  } catch (e) {
    setMessages((prev) => [
      ...prev,
      {
        id: Date.now() + 1,
        type: 'assistant',
        text: '죄송해요! 서버 연결에 문제가 생겼어요. (백엔드 실행/주소/CORS 확인)',
        timestamp: new Date(),
      },
    ]);
  } finally {
    setIsTyping(false);
  }
};

const handleSubmit = (e: React.FormEvent) => {
  e.preventDefault();
  sendMessage(inputText);
};

    

  // Intro Screen
  if (showIntro) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-6 md:p-12 bg-neutral-950">
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6 }}
          className="text-center space-y-12 max-w-4xl w-full"
        >
          {/* Animated Robot Character */}
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ 
              duration: 0.8, 
              delay: 0.2,
              type: "spring",
              stiffness: 200,
              damping: 15
            }}
            className="flex justify-center"
          >
            <div className="relative">
              {/* Glow effect */}
              <motion.div
                animate={{ 
                  scale: [1, 1.2, 1],
                  opacity: [0.5, 0.8, 0.5]
                }}
                transition={{ 
                  duration: 2,
                  repeat: Infinity,
                  ease: "easeInOut"
                }}
                className="absolute inset-0 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-full blur-3xl opacity-50"
              />
              
              {/* Robot icon */}
              <div className="relative w-40 h-40 md:w-56 md:h-56 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-3xl md:rounded-[3rem] flex items-center justify-center shadow-2xl">
                <motion.div
                  animate={{ 
                    rotate: [0, 10, -10, 0]
                  }}
                  transition={{ 
                    duration: 2,
                    repeat: Infinity,
                    ease: "easeInOut"
                  }}
                >
                  <Bot className="w-20 h-20 md:w-28 md:h-28 text-white" />
                </motion.div>
                
                {/* Sparkles */}
                <motion.div
                  animate={{ 
                    scale: [1, 1.2, 1],
                    rotate: [0, 180, 360]
                  }}
                  transition={{ 
                    duration: 3,
                    repeat: Infinity,
                    ease: "linear"
                  }}
                  className="absolute -top-3 -right-3 md:-top-4 md:-right-4"
                >
                  <Sparkles className="w-10 h-10 md:w-14 md:h-14 text-yellow-400" />
                </motion.div>
              </div>
            </div>
          </motion.div>

          {/* Title */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5, duration: 0.5 }}
            className="space-y-4"
          >
            <h1 className="text-4xl md:text-6xl text-white">
              안녕하세요, 패트리예요
            </h1>
            <p className="text-neutral-400 text-xl md:text-2xl">
              당신만의 스일을 찾아드릴게요
            </p>
          </motion.div>

          {/* Features */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.7, duration: 0.5 }}
            className="grid md:grid-cols-3 gap-4 md:gap-6 text-left max-w-3xl mx-auto"
          >
            {[
              { icon: '🎨', text: '체형과 피부톤에 맞는 색상 추천' },
              { icon: '👔', text: '상황별 코디 제안 및 조언' },
              { icon: '💡', text: '스타일 고민 해결 및 팁 제공' },
            ].map((feature, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.9 + index * 0.1, duration: 0.3 }}
                className="flex flex-col md:flex-row items-center md:items-start gap-4 p-5 md:p-6 bg-neutral-900 border border-neutral-800 rounded-xl hover:border-neutral-700 transition-colors"
              >
                <span className="text-3xl md:text-4xl">{feature.icon}</span>
                <span className="text-white text-base md:text-lg text-center md:text-left">{feature.text}</span>
              </motion.div>
            ))}
          </motion.div>

          {/* Start Button */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 1.2, duration: 0.5 }}
            className="max-w-md mx-auto"
          >
            <Button
              onClick={() => setShowIntro(false)}
              className="w-full bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 text-white py-7 md:py-8 rounded-xl text-lg md:text-xl shadow-lg hover:shadow-xl transition-shadow"
            >
              채팅 시작하기
            </Button>
          </motion.div>
        </motion.div>
      </div>
    );
  }

  // Main Chat Screen
  return (
    <div className="min-h-screen flex flex-col bg-neutral-950">
      {/* Header */}
      <header className="flex items-center justify-between px-6 md:px-8 py-4 border-b border-neutral-800">
        <div>
          <h1 className="text-xl md:text-2xl text-white">패트리</h1>
          <p className="text-xs text-neutral-500">AI 스타일 어시스턴트</p>
        </div>
        <div className="w-10 h-10 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-full flex items-center justify-center">
          <Bot className="w-5 h-5 text-white" />
        </div>
      </header>

      {/* Chat Area */}
      <div className="flex-1 overflow-y-auto p-6 md:p-8 pb-32 space-y-4 max-w-4xl w-full mx-auto">
        {messages.length === 0 ? (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="space-y-6 pt-8"
          >
            <div className="space-y-2">
              <h2 className="text-2xl text-white">
                어떤 코디를 만들어줄까요?
              </h2>
              <p className="text-neutral-400">
                스타일에 대한 모든 고민을 물어보세요
              </p>
            </div>

            <div className="space-y-3">
              {suggestedQuestions.map((question, index) => (
                <motion.button
                  key={question.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ duration: 0.3, delay: index * 0.1 }}
                  onClick={() => sendMessage(question.text)}
                  className="flex items-start gap-3 w-full p-4 bg-neutral-900 border border-neutral-800 hover:border-neutral-700 rounded-xl transition-colors text-left"
                >
                  <span className="text-2xl flex-shrink-0">{question.icon}</span>
                  <span className="text-white">{question.text}</span>
                </motion.button>
              ))}
            </div>

            {/* Fashion Tips Section */}
            <div className="pt-8 space-y-4">
              <div className="flex items-center gap-2">
                <Lightbulb className="w-5 h-5 text-yellow-400" />
                <h3 className="text-xl text-white">패션 상식</h3>
              </div>
              <p className="text-sm text-neutral-500">
                알아두면 유용한 스타일 팁을 확인해보세요
              </p>
              
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.4 }}
                className="bg-gradient-to-br from-neutral-900 to-neutral-900/50 border border-neutral-800 rounded-2xl p-6 md:p-8"
              >
                <div className="flex items-start gap-4 mb-4">
                  <span className="text-4xl md:text-5xl flex-shrink-0">{randomTip.icon}</span>
                  <h4 className="text-xl md:text-2xl text-white">{randomTip.title}</h4>
                </div>
                <p className="text-base md:text-lg text-neutral-300 leading-relaxed">
                  {randomTip.content}
                </p>
              </motion.div>
            </div>

            {/* Fashion Score Event Section */}
            <div className="pt-8 space-y-4">
              <div className="flex items-center gap-2">
                <Trophy className="w-5 h-5 text-yellow-500" />
                <h3 className="text-xl text-white">특별 이벤트</h3>
              </div>
              <p className="text-sm text-neutral-500">
                게임을 클리어하고 패션 점수를 확인해보세요!
              </p>
              
              <motion.button
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.5 }}
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={() => {
                  alert('패션 점수 게임은 곧 출시될 예정입니다! 🎮✨\n\n게임 내용:\n• 5가지 스타일 퀴즈\n• 체형/피부톤 매칭 퀴즈\n• 색상 조합 챌린지\n\n완료 시 당신의 패션 점수를 확인할 수 있어요!');
                }}
                className="w-full relative overflow-hidden bg-gradient-to-br from-yellow-500/20 via-orange-500/20 to-pink-500/20 border-2 border-yellow-500/50 rounded-2xl p-6 md:p-8 text-left group hover:border-yellow-500/70 transition-all"
              >
                {/* Animated Background */}
                <motion.div
                  animate={{
                    opacity: [0.3, 0.6, 0.3],
                    scale: [1, 1.1, 1],
                  }}
                  transition={{
                    duration: 3,
                    repeat: Infinity,
                    ease: "easeInOut"
                  }}
                  className="absolute inset-0 bg-gradient-to-br from-yellow-500/10 via-orange-500/10 to-pink-500/10"
                />

                {/* Content */}
                <div className="relative z-10">
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex items-center gap-3">
                      <div className="w-16 h-16 bg-gradient-to-br from-yellow-500 to-orange-500 rounded-2xl flex items-center justify-center shadow-lg">
                        <Trophy className="w-8 h-8 text-white" />
                      </div>
                      <div>
                        <h4 className="text-xl md:text-2xl text-white mb-1">내 패션 점수는 몇 점?</h4>
                        <p className="text-sm text-yellow-400">게임 클리어하고 점수 확인하기</p>
                      </div>
                    </div>
                    <motion.div
                      animate={{
                        rotate: [0, 10, -10, 0],
                        scale: [1, 1.1, 1, 1.1, 1],
                      }}
                      transition={{
                        duration: 2,
                        repeat: Infinity,
                        ease: "easeInOut"
                      }}
                    >
                      <Target className="w-8 h-8 text-yellow-400" />
                    </motion.div>
                  </div>

                  <div className="bg-black/20 backdrop-blur-sm rounded-xl p-4 space-y-2">
                    <p className="text-white">🎯 5가지 스타일 챌린지</p>
                    <p className="text-white">🎨 색상 매칭 퀴즈</p>
                    <p className="text-white">👔 체형별 코디 테스트</p>
                  </div>

                  <div className="mt-4 flex items-center justify-end">
                    <div className="flex items-center gap-1 text-white">
                      <span>시작하기</span>
                      <motion.div
                        animate={{ x: [0, 5, 0] }}
                        transition={{
                          duration: 1,
                          repeat: Infinity,
                          ease: "easeInOut"
                        }}
                      >
                        <ChevronRight className="w-5 h-5" />
                      </motion.div>
                    </div>
                  </div>
                </div>
              </motion.button>
            </div>
          </motion.div>
        ) : (
          <div className="space-y-4">
            {messages.map((message) => (
              <motion.div
                key={message.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
                className={`flex gap-2 ${message.type === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                {/* AI Avatar */}
                {message.type === 'assistant' && (
                  <div className="w-8 h-8 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-full flex items-center justify-center flex-shrink-0">
                    <Bot className="w-4 h-4 text-white" />
                  </div>
                )}
                
                <div
                  className={`max-w-[80%] rounded-2xl px-4 py-3 ${
                    message.type === 'user'
                      ? 'bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 text-white'
                      : 'bg-neutral-900 border border-neutral-800 text-white'
                  }`}
                >
                  {message.type === 'assistant' && (
                    <p className="text-xs text-blue-400 mb-1">패트리</p>
                  )}
                  <p className="whitespace-pre-wrap">{message.text}</p>
                </div>
              </motion.div>
            ))}

            {isTyping && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex gap-2 justify-start"
              >
                {/* AI Avatar */}
                <div className="w-8 h-8 bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 rounded-full flex items-center justify-center flex-shrink-0">
                  <Bot className="w-4 h-4 text-white" />
                </div>
                
                <div className="bg-neutral-900 border border-neutral-800 rounded-2xl px-4 py-3">
                  <p className="text-xs text-blue-400 mb-1">패트리</p>
                  <div className="flex gap-1">
                    <div className="w-2 h-2 bg-neutral-600 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <div className="w-2 h-2 bg-neutral-600 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                    <div className="w-2 h-2 bg-neutral-600 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  </div>
                </div>
              </motion.div>
            )}
          </div>
        )}
      </div>

      {/* Input Area */}
      <div className="sticky bottom-0 border-t border-neutral-800 p-4 bg-neutral-950 z-10">
        <form onSubmit={handleSubmit} className="flex items-center gap-2 max-w-4xl mx-auto">
          <div className="flex-1 relative">
            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="패션에 대해 무엇이든 물어보세요!"
              className="w-full bg-neutral-900 border border-neutral-800 rounded-full px-5 py-3 pr-12 text-white placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="button"
              className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-600 hover:text-neutral-400 transition-colors"
            >
              <Lightbulb className="w-5 h-5" />
            </button>
          </div>
          <Button
            type="submit"
            disabled={!inputText.trim() || isTyping}
            className="w-12 h-12 rounded-full bg-gradient-to-br from-blue-500 via-purple-600 to-pink-500 hover:from-blue-600 hover:via-purple-700 hover:to-pink-600 text-white flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed p-0"
          >
            <Send className="w-5 h-5" />
          </Button>
        </form>
      </div>
    </div>
  );
}