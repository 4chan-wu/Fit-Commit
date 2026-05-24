# 👕 Fitcommit (핏커밋)
> **"Record today's fit, clarify tomorrow's style."**
> **"오늘의 핏을 기록하고, 내일의 스타일을 더 선명하게"**

Fitcommit is an AI-powered fashion coordination and body management platform. It analyzes user body data in real-time and provides personalized styling solutions through the AI assistant **'Patree'**.

Fitcommit은 사용자의 신체 데이터를 정밀하게 기록하고, AI 어시스턴트 **'패트리(Patree)'**를 통해 개인화된 패션 솔루션을 제공하는 스마트 체형 관리 및 코디네이션 플랫폼입니다.

---

##  English

### ✨ Key Features
- **🤖 AI Coordinator 'Patree'**: Powered by **OpenAI API**, Patree provides professional styling advice based on the user's body data and TPO.
- **📸 Real-time Camera Body Scan**: Utilizes **AVFoundation** to capture and analyze body data through the device's camera.
- **📅 Scan History**: Manages a chronological history of body changes using **SwiftData** for seamless data persistence.
- **🛡️ Privacy & Security**: Protects sensitive body data with **Face ID (LocalAuthentication)**.
- **🎨 Glassmorphism UI**: A high-end user experience featuring transparent, blurred card designs that blend with the ambient background.

### 🛠 Tech Stack
- **Language**: Swift 5.10+
- **Framework**: SwiftUI
- **AI**: OpenAI API
- **Media**: AVFoundation (Real-time Camera API)
- **Database**: SwiftData
- **Security**: LocalAuthentication (Face ID)

---

##  한국어

### ✨ 핵심 기능
- **🤖 AI 코디네이터 '패트리'**: **OpenAI API**를 기반으로 사용자의 신체 데이터와 상황(TPO)에 맞는 전문적인 스타일링 제안을 제공합니다.
- **📸 실시간 카메라 체형 스캔**: **AVFoundation** 기술을 활용하여 실제 기기 카메라를 통해 체형 데이터를 캡처하고 분석합니다.
- **📅 스캔 히스토리**: **SwiftData**를 활용해 체형의 변화를 기록하고 관리하여 사용자에게 데이터 연속성을 제공합니다.
- **🛡️ 개인정보 보호 (Face ID)**: 민감한 신체 정보 접근 시 **Face ID(LocalAuthentication)** 인증 단계를 추가하여 보안을 강화했습니다.
- **🎨 Glassmorphism UI**: 투명도와 블러 효과를 극대화한 커스텀 카드 디자인 시스템으로 고급스러운 사용자 인터페이스를 구현했습니다.

### 🛠 기술 스택
- **언어**: Swift 5.10+
- **프레임워크**: SwiftUI
- **인공지능**: OpenAI API
- **미디어**: AVFoundation (실시간 카메라 API)
- **데이터베이스**: SwiftData
- **보안**: LocalAuthentication (Face ID 인증)

---

## 🏗️ Architecture & Logic
- **State Management**: Uses `@AppStorage` for persistent user settings and `@Binding` for seamless UI state transitions.
- **Navigation Flow**: Implements a custom navigation logic that intelligently hides the floating tab bar in detail views.
- **Data Modeling**: Sophisticated `@Model` design for efficient body profile and chat history management.

---

## 🚀 Getting Started
1. Clone the repository.
   ```bash
   git clone [https://github.com/your-id/Fitcommit.git](https://github.com/your-id/Fitcommit.git)

  1.	Open Fitcommit.xcodeproj in Xcode 15+.
	2.	Add your OpenAI API Key in the configuration file.
	3.	Build and run on a physical iOS device for camera functionality.

## 👤 Developer
•	Chan
•	Organization: Sahmyook University
•	profile: https://bit.ly/HeechanWooBusinessCard
