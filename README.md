## Priorify Backend

[![License](https://img.shields.io/badge/license-MIT-blue)]()
[![Backend](https://img.shields.io/badge/service-backend-green)]()

---

### 🌟 프로젝트 개요

Priorify는 사용자가 작업을 간편하게 우선순위화할 수 있도록 돕는 그래픽 to-do 웹 애플리케이션입니다. Fine-tuned LLaMA 모델을 활용한 Auto-categorization과 D3.js 기반의 동적 Visualization을 통해, 단순한 텍스트 입력만으로 중요도에 따라 색상과 크기가 변하는 Task graph를 제공합니다.

이 저장소는 Backend 구현(Spring Boot, MongoDB)을 포함하며, AWS EC2에 배포되어 있습니다. Frontend 코드 확인은 [priorify-frontend](https://github.com/JunSeo99/priorify-frontend.git)에서 가능합니다.

---

### 🚀 필수 기능

* **한 줄의 문장 입력만으로 Task 등록**
* **Fine-tuned LLaMA API로 우선순위 자동 분류**
* **D3.js를 이용해 중요도에 따라 크기·색상으로 표시**
* **Spring Boot RESTful API와 MongoDB storage on AWS EC2**
* **CI/CD**: GitHub Webhook & AWS CodeDeploy Agent로 자동화된 Deployment

---

### 🛠 Tech Stack

* **Backend**: Spring Boot, Spring Data MongoDB
* **Database**: MongoDB Atlas
* **AI**: Fine-tuned LLaMA API
* **Visualization**: D3.js (Frontend)
* **DevOps**: AWS EC2, CodeDeploy Agent, GitHub Webhooks

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

---

### 📥 시작하기

1. 저장소 클론:

   ```bash
   git clone https://github.com/JunSeo99/priorify-backend.git
   ```
2. `application.properties`에 MongoDB URI 및 LLaMA API credentials 설정
3. 빌드 및 실행:

   ```bash
   ./mvnw spring-boot:run
   ```

---
