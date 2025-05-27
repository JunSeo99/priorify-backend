# MongoDB Atlas Vector Search 설정 가이드

## 1. Atlas Vector Search Index 생성

MongoDB Atlas에서 다음 인덱스를 생성해야 합니다:

### Index 정보
- **Index Name**: `vector_index`
- **Collection**: `schedules`
- **Database**: `priorify`

### Index Definition (JSON)

```json
{
  "fields": [
    {
      "type": "vector",
      "path": "embedding",
      "numDimensions": 864,
      "similarity": "cosine"
    },
    {
      "type": "filter",
      "path": "userId"
    },
    {
      "type": "filter", 
      "path": "status"
    }
  ]
}
```

## 2. Atlas UI에서 설정하는 방법

1. **Atlas 콘솔 접속**
   - MongoDB Atlas에 로그인
   - 해당 클러스터 선택

2. **Search 탭으로 이동**
   - 클러스터 페이지에서 "Search" 탭 클릭
   - "Create Search Index" 버튼 클릭

3. **Atlas Vector Search 선택**
   - "Atlas Vector Search" 옵션 선택
   - Database: `priorify`
   - Collection: `schedules`

4. **Index 설정**
   - Index Name: `vector_index`
   - 위의 JSON Definition 붙여넣기

5. **배포**
   - "Create Search Index" 클릭
   - 인덱스 생성 완료까지 대기 (몇 분 소요)

## 3. CLI로 설정하는 방법

```bash
# MongoDB Atlas CLI 사용
atlas clusters search indexes create \
  --clusterName <cluster-name> \
  --file vector-index-definition.json

# vector-index-definition.json 파일 내용:
{
  "name": "vector_index",
  "database": "priorify",
  "collection": "schedules",
  "definition": {
    "fields": [
      {
        "type": "vector",
        "path": "embedding", 
        "numDimensions": 864,
        "similarity": "cosine"
      },
      {
        "type": "filter",
        "path": "userId"
      },
      {
        "type": "filter",
        "path": "status" 
      }
    ]
  }
}
```

## 4. 주요 설정 옵션 설명

- **numDimensions**: `864` - FastAPI 임베딩 모델의 차원 수
- **similarity**: `cosine` - 코사인 유사도 사용
- **filter**: `userId`, `status` - 빠른 필터링을 위한 인덱스

## 5. 성능 최적화 팁

1. **numCandidates 조정**
   - 현재: 100 (기본값)
   - 더 정확한 결과를 원하면 늘리기 (예: 200)
   - 성능을 원하면 줄이기 (예: 50)

2. **limit 값 조정**
   - 현재: 20개 후보 → 5개 최종 결과
   - 메모리와 성능을 고려해서 조정

3. **모니터링**
   - Atlas Performance Advisor에서 쿼리 성능 확인
   - Vector Search 사용량 모니터링

## 6. 인덱스 생성 확인

```javascript
// MongoDB Shell에서 확인
use priorify
db.schedules.aggregate([
  {
    $vectorSearch: {
      index: "vector_index",
      path: "embedding", 
      queryVector: [0.1, 0.2, 0.3, ...], // 테스트 벡터
      numCandidates: 10,
      limit: 5
    }
  }
])
```

인덱스가 제대로 생성되면 위 쿼리가 에러 없이 실행됩니다. 