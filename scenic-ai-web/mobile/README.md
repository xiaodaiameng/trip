# scenic-ai-web mobile

独立移动端 H5 工程，入口在 `mobile/`。

## 启动

```bash
cd scenic-ai-web/mobile
npm install
npm run dev
```

默认开发地址：

- `http://localhost:5174`

## 构建

```bash
npm run build
```

## Android 手机调试

如果要让手机直接访问本地后端，把 API 地址改成你电脑局域网 IP，例如：

```bash
set VITE_API_BASE=http://192.168.1.100:8080
```

然后再启动 `npm run dev`。
