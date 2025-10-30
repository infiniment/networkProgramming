# Swing + Socket 기반 양방향 채팅 프로젝트

## 프로젝트 개요
이 프로젝트는 **Java Swing GUI**와 **소켓 통신(Socket Programming)** 을 활용한 
**양방향 실시간 채팅 시스템**입니다.

GUI를 통해 사용자는 서버에 접속하여 채팅방을 생성하거나 참여할 수 있고,  
다양한 기능(이모티콘, 비밀 채팅, 자폭 메시지, 미니게임 등)을 통해  
풍부한 실시간 소통 환경을 제공합니다.

---

## 팀 구성 및 역할
| 역할 | 담당 | 주요 기능 |
|------|------|-----------|
| **팀원 A** | **클라이언트(Client)** | 다중 방 생성 기능, 미니게임(오목·베스킨라빈스31), 자폭 메시지 구현 |
| **팀원 B** | **서버(Server)** | 소켓 서버, 이모티콘 처리, 타이핑 중 표시, 비밀 채팅 모드, 슬래시 명령어 |

### 개발 흐름
1. 서버와 클라이언트를 각각 구현하여 단일방 채팅 테스트  
2. 다중 클라이언트 간 통신 및 GUI 연결 테스트  
3. 다중 방(Room) 생성 기능 추가  
4. 각자 맡은 확장 기능 구현 및 통합 테스트 진행

---

## ⚙️ 프로젝트 구조 (예시)
```
ChatApp/
├─ src/main/java/chat/
│ ├─ server/ # 서버 관련 코드 (팀원 B)
│ │ ├─ ChatServer.java
│ │ ├─ ClientHandler.java
│ │ ├─ RoomManager.java
│ │ ├─ CommandRouter.java
│ │ └─ ...
│ ├─ client/ # 클라이언트 관련 코드 (팀원 A)
│ │ ├─ ChatClient.java
│ │ ├─ ui/
│ │ │ ├─ LoginFrame.java
│ │ │ ├─ ChatFrame.java
│ │ │ └─ GamePanel.java
│ │ ├─ EmojiRenderer.java
│ │ └─ MiniGame/
│ │ ├─ OmokGame.java
│ │ └─ Br31Game.java
│ ├─ model/ # Message, Room, User 등 데이터 모델
│ ├─ dao/ # MySQL DAO (MessageDao, RoomDao 등)
│ └─ util/ # JsonUtil, Db, Logger 등
├─ build.gradle
└─ README.md
```


---

## 1차 구현 목표
1. **기본 채팅 연결**
   - 서버 소켓과 클라이언트 소켓 간 메시지 송수신
   - 1:1 통신 테스트 (Echo 형태)
2. **단일 채팅방(Room) 구현**
   - 여러 사용자가 동시에 채팅 가능
3. **MySQL 연동**
   - 사용자, 방, 메시지 데이터 저장
   - HikariCP 커넥션 풀 적용
4. **기본 GUI 완성**
   - 로그인 창 / 채팅 창 구분

---

## 2차 기능 확장
| 담당 | 기능 | 설명 |
|------|------|------|
| **팀원 A** | **다중 방 생성 기능** | 여러 개의 채팅방을 만들고 이동 가능 |
| **팀원 A** | **미니게임 (오목 / 베스킨라빈스31)** | 채팅방 내에서 간단한 게임 진행 |
| **팀원 A** | **자폭 메시지(Self-Destruct)** | TTL이 지난 메시지 자동 삭제 |
| **팀원 B** | **이모티콘 기능** | 이미지 기반 |
| **팀원 B** | **타이핑 중 표시** | 상대방이 입력 중임을 실시간 표시 |
| **팀원 B** | **시크릿 채팅 모드 (secrete)** | ON, OFF로 비밀 채팅을 치고 OFF 하면 ON 했을 때 채팅한 것들이 다 사라짐 |
| **팀원 B** | **슬래시 명령어(Command)** | `/w`, `/me`, `/br`, `/omok` 등 명령어 처리 |

---

## 데이터베이스 설계
- **MySQL 8.x** 사용 (커넥션 풀: HikariCP)
- 주요 테이블:
  - `users` — 사용자 정보
  - `rooms` — 방 정보
  - `messages` — 채팅 메시지 (TTL, 삭제 여부 포함)
  - `message_recipients` — 귓속말 수신자 매핑
- Flyway로 DB 버전 관리

---

## Gradle 주요 라이브러리
| 라이브러리 | 용도 |
|-------------|------|
| `mysql:mysql-connector-j` | MySQL JDBC 드라이버 |
| `com.zaxxer:HikariCP` | 커넥션 풀 관리 |
| `com.google.code.gson:gson` | JSON 직렬화/역직렬화 |
| `org.flywaydb:flyway-core` | DB 마이그레이션 관리 |
| `ch.qos.logback:logback-classic` | 로그 관리 |
| `org.junit.jupiter:junit-jupiter` | 단위 테스트 |

---

## 개발 순서 요약
1. 서버 ↔ 클라이언트 단일 채팅 테스트  
2. 다중 클라이언트 접속 및 단일 방 브로드캐스트  
3. 다중 방 생성 / 전환 기능 (A 담당)  
4. 이모티콘 / 비밀 채팅 / 타이핑 표시 / 명령어 (B 담당)  
5. 자폭 메시지 / 미니게임 (A 담당)  
6. 전체 통합 테스트

---

## 실행 방법
```bash
# 서버 실행 (팀원 B)
$ java -cp build/libs/ChatApp.jar chat.server.ChatServer

# 클라이언트 실행 (팀원 A)
$ java -cp build/libs/ChatApp.jar chat.client.ChatClient
