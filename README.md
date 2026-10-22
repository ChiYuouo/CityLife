# CityLife

CityLife 是一个本地生活服务示例项目，提供商户浏览、探店笔记、关注、签到和优惠券等功能。后端使用 Spring Boot 3、MySQL 分库、Redis 和 Kafka，前端使用 Vue 3。

## 一键启动

安装 Docker Desktop 并确保 Docker Engine 正在运行，在项目根目录执行：

```sh
docker compose up -d --build
```

首次构建会下载镜像和依赖，并自动创建 `citylife_0`、`citylife_1` 两个数据库及演示数据。服务启动后访问 [http://localhost:18080](http://localhost:18080)。可通过环境变量 `CITYLIFE_PORT` 修改本机端口。后端通过同一地址的 `/api/` 访问，无需单独配置跨域。

查看运行状态：

```sh
docker compose ps
docker compose logs -f backend
```

停止服务：

```sh
docker compose down
```

数据库、Redis 和 Kafka 使用 Docker 命名卷保存数据；上传图片保存在项目的 `data/uploads/`。再次执行 `docker compose up -d --build` 不会重复导入数据库。需要重新导入演示数据时，可在确认不需要现有数据后执行 `docker compose down -v`，并清理 `data/uploads/`。

## 项目结构

学习项目可从 [技术全景与源码导读](docs/技术全景与源码导读.md) 开始；准备简历时再看 [简历候选要点](docs/简历候选要点.md) 和 [面试追问与回答](docs/面试追问与回答.md)。[入门速览](docs/CityLife学习指南.md) 保留为简短阅读路线。

- `citylife-core-service/`：Spring Boot API
- `citylife-web/`：Vue 3 前端
- `citylife-common/`、`citylife-*-framework/`、`citylife-parameter/`、`citylife-sharding/`：后端公共模块
- `sql/`：数据库结构和演示数据

项目名称、Maven 模块、Java 包名、数据库名、应用名和页面标题已统一为 CityLife。项目使用 Apache License 2.0，详见 [LICENSE](LICENSE)。
