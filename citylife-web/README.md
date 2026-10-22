# CityLife 前端

这是 CityLife 的 Vue 3 前端。完整服务请在项目根目录运行 `docker compose up -d --build`。

本地开发：

```sh
npm ci
npm run dev
```

开发服务器将 `/api/` 代理到本机的 `8085` 端口。生产环境由 Nginx 代理后端。
