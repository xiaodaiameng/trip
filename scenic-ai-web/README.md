# scenic-ai-web

## 当前状态

这个前端项目目前还是一个 Vue 3 + TypeScript + Vite 的脚手架页面，还没有完成景区导览业务界面。

当前代码实际情况：

- `src/App.vue` 仅挂载 `HelloWorld.vue`
- `src/components/HelloWorld.vue` 仍是默认模板内容
- 还没有请求后端接口
- 还没有游客端、管理端、数据看板页面

所以它现在更像“前端工程壳子”，不是完成态前端。

## 技术栈

- Vue 3
- TypeScript
- Vite

## 本地启动

```bash
npm install
npm run dev
```

默认开发地址一般为：

- [http://localhost:5173](http://localhost:5173)

## 构建

```bash
npm run build
```

## 建议的下一步页面拆分

建议先按最小可演示范围拆成 4 个页面或视图：

1. 游客首页
   - 展示景区简介、热门问题、景点入口
2. 智能问答页
   - 输入问题、展示答案、展示来源、反馈是否有帮助
3. 路线推荐页
   - 选择兴趣、时长、节奏、同行类型并生成路线
4. 管理看板页
   - 展示问答量、满意度、热门问题、热门景点

## 建议优先接入的接口

后端基础地址默认是：

- [http://localhost:8080](http://localhost:8080)

推荐先接这些接口：

- `GET /api/public/overview`
- `GET /api/public/attractions`
- `POST /api/public/chat`
- `POST /api/public/routes/recommend`
- `POST /api/public/feedback`
- `POST /api/admin/login`
- `GET /api/admin/dashboard`
- `GET /api/admin/knowledge`

## 当前明显待办

- 替换默认模板页面
- 建立 API 请求层
- 定义 TypeScript 接口类型
- 做基础布局和导航
- 处理后端中文乱码带来的显示问题

## 推荐开发顺序

1. 先做公共布局和接口封装
2. 再做游客问答页和路线推荐页
3. 然后做管理看板页
4. 最后补知识库管理页和界面细节
