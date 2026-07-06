# Team Agents — InnerStyle Project

Thư mục này chứa định nghĩa cho từng AI agent trong team. Mỗi file `.md` là một "system prompt" mô tả vai trò, trách nhiệm, quy tắc và template output của agent đó.

---

## Danh sách agents

| Agent | File | Vai trò | Chuyên môn |
|-------|------|---------|------------|
| **BA** | [ba-agent.md](./ba-agent.md) | Business Analyst | SRS, Use Case, Business Rules, UML, API Contract |
| **Backend Dev** | [backend-dev-agent.md](./backend-dev-agent.md) | Spring Boot Developer | Entity, Repository, Service, Controller, Migration |
| **Frontend Dev** | [frontend-dev-agent.md](./frontend-dev-agent.md) | React + Vite Developer | Components, Hooks, Tailwind, i18n, API integration |
| **Tester** | [tester-agent.md](./tester-agent.md) | QA Engineer | JUnit, Mockito, Playwright E2E, Postman |
| **Architect** | [architect-agent.md](./architect-agent.md) | Solution Architect | Database, AWS, Redis, RabbitMQ, Microservices |
| **Reviewer** | [reviewer-agent.md](./reviewer-agent.md) | Code Reviewer | Security, Performance, Standards, Bug detection |

---

## Cách sử dụng

### Với Claude / AI Agent
Đặt nội dung file `.md` vào đầu conversation như một system prompt, hoặc paste vào đầu mỗi request:

```
[Dán nội dung ba-agent.md vào đây]

---
Yêu cầu: Viết Use Case Specification cho tính năng "Người dùng tạo đơn in 3D"
```

### Phân công theo task

| Loại task | Dùng agent |
|-----------|-----------|
| Phân tích yêu cầu khách hàng | BA |
| Viết API mới | BA (contract) → Backend Dev (implementation) |
| Xây dựng UI component | Frontend Dev |
| Thiết kế DB schema | Architect |
| Lựa chọn queue/cache solution | Architect |
| Viết test cho service mới | Tester |
| Review PR trước merge | Reviewer |
| Fix bug production | Backend Dev + Reviewer |

---

## Quy trình làm việc đề xuất

```
BA → thiết kế yêu cầu, API contract
    ↓
Architect → thiết kế DB, infra
    ↓
Backend Dev → implement API
    ↓
Frontend Dev → implement UI
    ↓
Tester → viết và chạy test
    ↓
Reviewer → review code, approve merge
```

---

## Ghi chú

- Mỗi agent biết rõ stack và convention của dự án InnerStyle
- Reviewer agent được thiết kế để bổ sung cho tất cả agents còn lại
- BA agent nên được dùng đầu tiên khi có yêu cầu mới từ stakeholder
- Architect agent cần được tham vấn khi có thay đổi ảnh hưởng đến infra hoặc data model
