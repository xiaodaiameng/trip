# 灵境智游

一个面向景区导览场景的前后端分离项目，提供游客端问答、路线推荐、地图展示、数字人讲解，以及后台运营摘要和知识库维护能力。

## Features

- 游客端景点浏览、热门问题、路线推荐、地图导航
- 数字人讲解、语音播报、移动端听写输入
- 后台登录、运营摘要、问答记录查看
- 知识条目管理与资料包下载入口
- 桌面端与移动端分离适配

## Project Structure

```text
softbei/
├─ scenic-ai-server/      # Spring Boot backend
├─ scenic-ai-web/         # Desktop web frontend (Vue 3 + Vite)
├─ scenic-ai-web/mobile/  # Mobile web frontend (Vue 3 + Vite)
├─ docs/                  # Project docs
├─ 示范景区公开资料包/      # Scenic source materials
└─ start-all.bat          # One-click local startup script
```

## Tech Stack

- Backend: Spring Boot 3, Spring Web, Spring Data JPA, Spring Security
- Frontend: Vue 3, Vite, TypeScript
- 3D / Visual: Three.js, OGL, Leaflet
- Database:
  - Local demo: H2
  - Optional: MySQL

## Main Modules

### Public APIs

- `GET /api/public/overview`
- `GET /api/public/attractions`
- `POST /api/public/chat`
- `POST /api/public/routes/recommend`
- `POST /api/public/feedback`
- `GET /api/public/tts/voices`
- `POST /api/public/tts`

### Admin APIs

- `POST /api/admin/login`
- `GET /api/admin/dashboard`
- `GET /api/admin/records`
- `GET /api/admin/knowledge`
- `POST /api/admin/knowledge`

## Quick Start

### Requirements

- Java 19
- Maven
- Node.js + npm

### Start Everything

在仓库根目录运行：

```bat
start-all.bat
```

默认会启动：

- Backend: `http://localhost:8080`
- Desktop web: `http://localhost:5173`
- Mobile web: `http://localhost:5174`

健康检查：

```text
http://localhost:8080/actuator/health
```

## Default Demo Admin

- Username: `admin`
- Password: `admin123`

`start-all.bat` 会自动注入本地演示环境变量，其中后台密码来自预置的 `ADMIN_PASSWORD_HASH`。

## Notes

- 当前问答能力主要基于本地景点资料和知识条目匹配，不是在线大模型生成。
- 资料包下载文件已放入静态资源目录，可从前端页面直接下载。
- 如果需要切换数据库或管理员配置，优先查看：
  - `scenic-ai-server/src/main/resources/application.yml`
  - `scenic-ai-server/src/main/resources/application-dev.yml`

## License

当前仓库未附带单独许可证文件，如需开源发布，建议补充 `LICENSE`。
