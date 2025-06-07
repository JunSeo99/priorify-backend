# Priorify major backend

> “누구나 J가 될 수 있도록”  
> 일정 관리의 복잡함을 그래프로 시각화하여, 어떤 일을 먼저 처리해야 할지 명확하게 알려주는 AI 기반 스케줄링 서비스

[![Backend](https://img.shields.io/badge/service-backend-green)]()



---

[![🖥️ Frontend](https://img.shields.io/badge/Frontend-Next.js-151515?style=for-the-badge&logo=next.js&logoColor=white)](https://github.com/JunSeo99/priorify-backend-frontend)

[![⚡ FastAPI Server](https://img.shields.io/badge/Backend-FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://github.com/JunSeo99/priorify-backend-text2vec)


## 🚀 프로젝트 개요

Priorify는 사용자의 Google 캘린더 일정을 불러와  
1. **텍스트 임베딩** (Text2Vec + NER)  
2. **유사도 기반 Vector Search** (MongoDB Atlas Vector Search)  
3. **그래프 시각화 & 카테고리 자동 분류**  
4. **우선순위 자동 결정**  

해당 레포지토리는 **Spring Boot**(Service API)입니다.

---

## 📦 주요 기술 스택

| 구성 요소       | 기술/라이브러리                                    |
| -------------- | -------------------------------------------------- |
| 주요 프레임 워크    | Spring Boot, Spring Data MongoDB, RxJava      |
| AI모델    | Text2Vec (e.g., Ko-SBERT), NER (KoELECTRA)             |
| 데이터베이스    | MongoDB Atlas (Vector Search)                      |
| CI/CD          | GitHub Actions → Jenkins → AWS EC2 (Docker)        |
| 인증/연동      | OAuth2 (Google Login & Calendar API)               |

### 🚀 필수 기능

* **한 줄의 문장 입력만으로 Task 등록**
* **Text2Vec과 NER 모델을 활용한 스케줄 자동 카테고리 분류**
* **D3.js를 이용해 중요도에 따라 크기·색상으로 표시**
* **Spring Boot RESTful API와 MongoDB storage on AWS EC2**
* **CI/CD**: GitHub Webhook & AWS CodeDeploy Agent로 자동화된 Deployment

---

### 📆 개발 일정

| Week | Milestone                              |
| :--: | :------------------------------------- |
|   1  | Project selection & initial research   |
|   2  | Repo fork & environment setup          |
|   3  | Git branching strategy & CI pipeline   |
|   4  | Authentication & basic to-do endpoints |
|   5  | AI-driven auto-categorization          |
|   6  | Integration with D3.js visualization   |
|   7  | UI/UX polish & end-to-end testing      |
|   8  | Final review & documentation           |

---

### 👥 팀원

* **PM / Front-end**: 송준서 (32202337, Department of Software, Dankook University)
* **Back-end**: 윤치호 (32227546, Department of Software, Dankook University)
* **Back-end**: 이지훈 (32243528, Department of Software, Dankook University)

---

### 🚦 Git Workflow

1. Fork the repository
2. Create feature branch: `feature/<name>`
3. Open Pull Request
4. Code review & merge into develop
5. Merge develop into main
