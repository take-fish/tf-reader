# tf-reader

FishMatrix（摸鱼矩阵）阅读服务：书籍内容、阅读进度与协同评审 —— Spring Boot 3 + JDK 17。

- 端口：`8103`
- 健康检查：`GET /actuator/health`、`GET /api/v1/reader/health`

## 本地开发

```bash
mvn spring-boot:run    # 启动开发服务
mvn -B test            # 运行测试
```

## 构建

```bash
mvn -B -DskipTests package   # 产物 target/tf-reader.jar
java -jar target/tf-reader.jar
```

## 分支模型

- `master`：保护分支，只接受 PR（CI 必须通过），合并后自动发版
- `test`：联调分支，可直接推送
- 开发流：`feature/*` → PR 到 `test` → 联调 → PR 到 `master` → 自动发版
