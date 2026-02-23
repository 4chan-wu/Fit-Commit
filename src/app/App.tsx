import React, { useState } from 'react';
import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import ClosetPage from './pages/ClosetPage';
import StyleChatPage from './pages/StyleChatPage';
import MyPage from './pages/MyPage';
import AIDiagnosis from './components/AIDiagnosis';
import SideNav from './components/SideNav';
import { ClosetProvider } from './context/ClosetContext';

type Page = 'main' | 'login' | 'home' | 'closet' | 'style-chat' | 'my' | 'ai-diagnosis';

function App() {
  const [currentPage, setCurrentPage] = useState<Page>('main');
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  const handleLogin = () => {
    setIsLoggedIn(true);
    setCurrentPage('home');
  };

  const handleStartDiagnosis = () => {
    setCurrentPage('ai-diagnosis');
  };

  const handleDiagnosisComplete = () => {
    setCurrentPage('home');
  };

  const showSideNav = isLoggedIn && currentPage !== 'ai-diagnosis' && currentPage !== 'main' && currentPage !== 'login';

  // Full page views (no nav)
  const isFullPageView = currentPage === 'main' || currentPage === 'login' || currentPage === 'ai-diagnosis';

  return (
    <ClosetProvider>
      <div className="min-h-screen bg-neutral-950 text-white">
        {isFullPageView ? (
          // Mobile-centered layout for onboarding flows
          <div className="max-w-md mx-auto min-h-screen bg-neutral-950 relative">
            {currentPage === 'main' && <MainPage onGetStarted={() => setCurrentPage('login')} />}
            {currentPage === 'login' && <LoginPage onLogin={handleLogin} />}
            {currentPage === 'ai-diagnosis' && <AIDiagnosis onComplete={handleDiagnosisComplete} />}
          </div>
        ) : (
          // Desktop layout with sidebar
          <div className="flex min-h-screen">
            {showSideNav && <SideNav currentPage={currentPage} onNavigate={setCurrentPage} />}
            
            <main className="flex-1 min-h-screen">
              <div className="max-w-6xl mx-auto">
                {currentPage === 'home' && <HomePage onStartDiagnosis={handleStartDiagnosis} />}
                {currentPage === 'closet' && <ClosetPage />}
                {currentPage === 'style-chat' && <StyleChatPage />}
                {currentPage === 'my' && <MyPage />}
              </div>
            </main>
          </div>
        )}
      </div>
    </ClosetProvider>
  );
}

export default App;