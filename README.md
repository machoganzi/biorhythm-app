# 📦 프로젝트 구조

com.jjangdol.biorhythm
├─ data
│  ├─ FirebaseModule.kt
│  ├─ ResultsRepository.kt
│  ├─ SettingsRepository.kt
│  ├─ UserRepository.kt
│  └─ WeightSettings.kt
├─ di
│  └─ RepositoryModule.kt
├─ model
│  ├─ BiorhythmData.kt
│  ├─ ChecklistConfig.kt
│  ├─ ChecklistItem.kt
│  ├─ ChecklistResult.kt
│  └─ ChecklistWeight.kt
├─ ui
│  ├─ admin
│  │  ├─ AdminFragment.kt
│  │  ├─ SettingsAdapter.kt
│  │  ├─ AdminResultsFragment.kt
│  │  └─ ResultsAdapter.kt
│  ├─ checklist
│  │  ├─ ChecklistFragment.kt
│  │  └─ ChecklistAdapter.kt
│  ├─ login
│  │  └─ LoginFragment.kt
│  ├─ main
│  │  ├─ MainFragment.kt
│  │  └─ NotificationFragment.kt
│  └─ result
│     └─ ResultFragment.kt
├─ util
│  └─ ScoreCalculator.kt
├─ vm
│  ├─ AdminResultsViewModel.kt
│  ├─ BiorhythmViewModel.kt
│  ├─ ChecklistViewModel.kt
│  ├─ LoginViewModel.kt
│  ├─ ResultsViewModel.kt
│  └─ SettingsViewModel.kt
├─ BioApp.kt
└─ MainActivity.kt

res/layout
├─ activity_main.xml
├─ fragment_admin.xml
├─ fragment_admin_results.xml
├─ fragment_checklist.xml
├─ fragment_login.xml
├─ fragment_main.xml
├─ fragment_notification.xml
├─ fragment_result.xml
├─ item_admin_result.xml
├─ item_checklist.xml
├─ item_result.xml
├─ item_settings.xml
└─ item_weight.xml

## 📂 data (서버·저장소 담당)

- **FirebaseModule.kt**: 앱 전체에서 Firebase(auth, firestore 등)를 한 번만 만들어 쓰도록 등록해 주는 곳
    
- **SettingsRepository.kt**:
    
    - 관리자 설정(“어떤 질문이 있고, 각 질문 가중치는 얼마인가?”) 읽고 쓸 때 쓰는 코드
        
    - 경로: `settings/weights/checklist/...`
        
- **ResultsRepository.kt**:
    
    - 관리자가 “오늘 누가 위험군/비위험군인지” 실시간으로 받아올 때 쓰는 코드
        
    - 경로: `results/{오늘날짜}/entries`
        
- **UserRepository.kt**:
    
    - 로그인한 사용자의 이름·부서·DOB을 서버에 저장하거나 불러오는 코드
        
- **WeightSettings.kt**: Firestore에 저장된 “질문 + 가중치” 한 세트를 Kotlin 객체로 표현한 모델
    

---

## 📂 model (앱에서 주고받는 데이터 모양)

- **ChecklistConfig.kt**: “질문+가중치” 설정을 그대로 담는 모델
    
- **ChecklistItem.kt**: 실제 체크리스트 화면에서 쓰는, “질문+가중치 + Yes/No 답” 모델
    
- **ChecklistResult.kt**: 최종 점수(체크리스트, 바이오리듬, 합산) + 사용자 정보 담는 모델
    
- **ChecklistWeight.kt**: 설정 화면 전용 “질문+가중치” 간단 모델
    
- **BiorhythmData.kt**: 바이오리듬 계산 결과(날짜별 물리·감정·지성 값) 담는 모델
    

---

## 📂 ui (화면 담당)

### 🖥🏻 로그인 · 메인

- **LoginFragment.kt**:
    
    - 이름·부서·생일 입력 후 Firebase Auth/Firestore에 저장 → 메인 화면으로 이동
        
- **MainFragment.kt**: 하단바로 “체크리스트 / 결과 / 관리자” 화면 전환
    
- **NotificationFragment.kt**: (기본 제공) 공지용 빈 화면
    

### ✅ 체크리스트 (일반 사용자)

- **ChecklistFragment.kt**:
    
    1. 서버에서 질문·가중치를 가져와서
        
    2. “예/아니오” 체크 UI 띄우고
        
    3. 누르면 점수 계산 → “체크리스트 점수 + 바이오리듬 점수” 합쳐서 서버에 저장
        
- **ChecklistAdapter.kt**: 질문 리스트를 보여주고, Yes/No 버튼 누를 때 ViewModel에 알림
    

### 📊 결과 (일반 사용자)

- **ResultFragment.kt**:
    
    - 오늘 본인이 낸 점수(체크리스트·바이오지수·합산)를 불러와서
        
    - 숫자 + 간단한 차트로 화면에 보여줌
        

### ⚙️ 관리 (관리자)

- **AdminFragment.kt**:
    
    - 질문을 추가·삭제·가중치 수정 → 바로바로 서버에 반영
        
- **SettingsAdapter.kt**: 설정 리스트 보여주고, 숫자 에디트·삭제 버튼 처리
    
- **AdminResultsFragment.kt**:
    
    - 오늘 모든 사용자 최종점수를 불러와서
        
    - “위험군 / 비위험군” 두 목록으로 분리해 보여줌
        
- **ResultsAdapter.kt**: 각 사용자 결과(이름·부서·점수) 한 줄씩 그려주는 어댑터
    

---

## 🔧 util · vm (계산·상태 담당)

- **ScoreCalculator.kt**:
    
    - 체크리스트 합산 점수
        
    - 바이오리듬 지수 계산
        
    - 최종점수(단순 평균) 계산
        
- **BiorhythmViewModel.kt**:
    
    - “생일 → 오늘 기준 ±15일 sine 곡선 값”을 만들어 `LiveData`처럼 제공
        
- **ChecklistViewModel.kt**:
    
    - Firestore에서 질문·가중치 받아와서 `ChecklistItem` 목록 관리
        
    - 사용자 답변(Yes/No) 상태도 들고 있음
        
- **SettingsViewModel.kt**:
    
    - 관리자 설정(문항/가중치) CRUD 기능
        
- **ResultsViewModel.kt / AdminResultsViewModel.kt**:
    
    - 관리자 화면이 쓰는 “오늘 점수 목록” `Flow`를 래핑
        
- **LoginViewModel.kt**:
    
    - Firebase Auth·Firestore 로그인 로직 실행 후 상태(`Loading`/`Success`/`Error`) 알려줌
        

---

**한 문장 요약**

1. **data** → 서버 읽고 쓰는 코드
    
2. **model** → 데이터 모양(질문·답·결과 등)
    
3. **ui** → 화면(Fragment + Adapter)
    
4. **util** → 점수 계산기
    
5. **vm** → 화면에 필요한 데이터 흐름 관리

## 레이아웃

- **activity_main.xml**
    
    - `NavHostFragment`를 포함한 액티비티 기본 구조.
        
- **fragment_*.xml**
    
    - 각 Fragment에 대응하는 화면 구성.
        
        - `fragment_admin.xml` (문항 설정)
            
        - `fragment_admin_results.xml` (전체 결과)
            
        - `fragment_checklist.xml` (체크리스트)
            
        - `fragment_login.xml`, `fragment_main.xml`, `fragment_notification.xml`, `fragment_result.xml`
            
- **item_*.xml**
    
    - RecyclerView 각 행 뷰.
        
        - `item_settings.xml` (문항+가중치)
            
        - `item_checklist.xml` (문항+Yes/No)
            
        - `item_result.xml` (사용자 결과)
            
        - `item_weight.xml` (설정용 가중치 단일 뷰, 필요 시)
