# CityLife 学习指南

> 这是一份入门速览。更完整的代码核对、故障边界和技术索引见[技术全景与源码导读](技术全景与源码导读.md)；简历素材与答辩准备见[简历候选要点](简历候选要点.md)和[面试追问与回答](面试追问与回答.md)。

> 这是一份针对当前 CityLife 代码的原创学习笔记，不是原站教程的镜像。整理于 2026-10-07。原作者网站的部分章节只公开前言，正文提示付费；这里不绕过访问限制，也不把未看到的内容当作已读资料。外部资料用于确定学习主题，具体流程以本地代码为准。

## 先抓住一条主线

这个项目最适合从「用户抢一张券」入手，而不是按 Maven 模块从头到尾读。一次请求大致经过：

1. 用户访问 `GET /voucher-order/seckill/token/{id}`。控制器先限流，再签发短期访问令牌。
2. 用户携带令牌访问 `POST /voucher-order/seckill/{id}`。控制器再次限流，并消费一次性访问令牌。
3. 业务服务读取券信息、检查用户资格，用雪花算法生成订单 ID 和追踪 ID。
4. Redis Lua 脚本一次完成时间/状态/库存/一人一单检查，以及 Redis 库存扣减、购买用户记录、追踪日志写入。
5. 脚本成功后，服务向 Kafka 发送下单消息，并立即返回订单 ID。
6. Kafka 消费者随后创建数据库订单；失败和超时路径有回滚与对账处理。

入口在 [VoucherOrderController.java](../citylife-core-service/src/main/java/com/citylife/controller/VoucherOrderController.java)，当前实际调用的是 [VoucherOrderServiceImpl.java](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherOrderServiceImpl.java) 中的 `doSeckillVoucherV2`，不是保留作参考的 V1。**返回订单 ID 表示请求进入异步流程，不等于数据库订单已经创建成功。**随后要看 [SeckillVoucherConsumer.java](../citylife-core-service/src/main/java/com/citylife/kafka/consumer/SeckillVoucherConsumer.java) 和 [ReconciliationTaskServiceImpl.java](../citylife-core-service/src/main/java/com/citylife/service/impl/ReconciliationTaskServiceImpl.java)。

## 模块地图

| 目录 | 简单理解 | 建议从哪里看 |
| --- | --- | --- |
| `citylife-web` | Vue 页面，发起请求、展示结果 | `src`、接口调用处 |
| `citylife-core-service` | 业务主程序：接口、服务、数据库映射、消息消费 | `controller` -> `service/impl` -> `mapper` |
| `citylife-common` | 公共返回值、常量、异常等 | 遇到引用时再查 |
| `citylife-parameter` | 跨模块传递的参数和数据对象 | 跟着调用链查字段 |
| `citylife-id-generator-framework` | 雪花 ID 和节点编号分配 | `SnowflakeIdGenerator` |
| `citylife-redis-tool-framework` | Redis 封装、Lua、限流、访问令牌等 | `RedisRateLimitHandler`、`resources/lua` |
| `citylife-mq-framework` | Kafka 消息发送/消费的通用封装 | 先读业务生产者/消费者，再下钻 |
| `citylife-redisson-framework` | 分布式锁、延迟队列等封装 | 遇到实际调用点再读 |
| `citylife-sharding` | 分片算法/数据源配置 | 配合 `shardingsphere.yaml` |

模块可以理解成「按职责分抽屉」。读代码时不要在一个抽屉里读完才换下一个：沿一次请求走，碰到哪个抽屉才打开哪个。

## 三个核心概念

### 1. Lua 脚本：把一组 Redis 操作连起来

问题：假设库存只剩 1，两个人同时执行「先读库存，再判断，再扣减」。如果这些是分开的 Redis 命令，两人可能都读到 1。Lua 脚本把相关判断和修改放在 Redis 的一次脚本执行中，其他命令不会插进脚本执行过程。

本项目的 [seckillVoucher.lua](../citylife-core-service/src/main/resources/lua/seckillVoucher.lua) 使用 `KEYS` 接收库存、已购买用户集合、追踪日志键，使用 `ARGV` 接收券 ID、用户 ID、活动时间、订单 ID 等参数。数据变化可以这样看：

```text
执行前：stock=2，buyers={}
用户 A 通过检查：stock=1，buyers={A}，写入追踪日志
用户 A 再请求：SISMEMBER 命中，拒绝；stock 仍是 1
```

注意两个边界：Lua 只保证这**一次 Redis 脚本**的执行不被其他 Redis 命令穿插，并不把后面的 Kafka 发送和 MySQL 写入变成一个跨系统事务；而且脚本运行中如果发生错误，之前已经执行的 Redis 写入也不会自动回滚。因此仍需要失败补偿和对账。当前代码的 Redis 回滚脚本会让库存缓存失效，并处理用户集合/追踪日志，不能简单理解为「原数值直接加 1」。

除了秒杀，项目还用 Lua 做 [令牌桶](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/resources/lua/tokenBucket.lua)、[一次性访问令牌消费](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/resources/lua/seckill_access_token.lua)、[雪花节点编号分配](../citylife-id-generator-framework/src/main/resources/lua/workAndDataCenterId.lua)。读 Lua 时先写下 `KEYS`、`ARGV`、Redis 数据结构，再逐行追踪「读了什么、改了什么、返回什么」。

### 2. 雪花 ID：不同实例各自生成、不必每次问数据库

一个 64 位整数由时间、数据中心编号、工作节点编号、同一毫秒内的序号等部分拼成。当前 [SnowflakeIdGenerator.java](../citylife-id-generator-framework/src/main/java/com/citylife/toolkit/SnowflakeIdGenerator.java) 使用 41 位时间、5 位数据中心、5 位节点、12 位序号，最高位不使用；同一实例同一毫秒连续生成时，序号递增。序号耗尽会等下一毫秒；时钟回拨会等待或报错。

直观例子：A、B 两个服务实例同一毫秒都要生成订单号，只有它们的「数据中心 + 节点」组合不同，生成的 ID 才不会撞。项目通过 [WorkAndDataCenterIdHandler.java](../citylife-id-generator-framework/src/main/java/com/citylife/toolkit/WorkAndDataCenterIdHandler.java) 调 Redis Lua 分配编号。但**不要把“用了 Redis 分配”直接说成任意部署规模下绝对不重复**：分配脚本会在编号上限处回绕，部署前还应验证重启、Redis 数据丢失和超过可用组合数时的行为。

### 3. 令牌桶：允许短时突发，同时控制长期速率

把桶想成最多能装 3 枚通行筹码：初始有 3 枚，每个请求拿走 1 枚，每秒补 1 枚，最多补到 3 枚。紧接着来的前 3 个请求可通过，第 4 个被挡住；过 1 秒又可通过 1 个。代码中的 [tokenBucket.lua](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/resources/lua/tokenBucket.lua) 用 Redis Hash 保存剩余 `tokens` 和上次计算时间 `last_ms`，用 Redis `TIME` 计算补充量，再决定是否扣掉 1 枚。

[RedisRateLimitHandler.java](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/java/com/citylife/execute/RedisRateLimitHandler.java) 同时检查 IP 桶和用户桶。默认参数见 [SeckillRateLimitConfigProperties.java](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/java/com/citylife/config/SeckillRateLimitConfigProperties.java)：IP 桶容量 3、约 5 秒补满；用户桶容量 5、约 60 秒补满。场景可覆盖配置；`enableSlidingWindow=false` 时默认走令牌桶，改为 `true` 才走滑动窗口。这里的「5 秒/60 秒」用于算补充速率，**不是每到整 5 秒/60 秒清零一次的固定窗口**。

还有一个容易混淆的同名概念：`/seckill/token/{id}` 签发的是**一次性访问令牌**，用于证明这次用户请求拿到了秒杀资格，见 [SeckillAccessTokenServiceImpl.java](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/java/com/citylife/service/impl/SeckillAccessTokenServiceImpl.java)；令牌桶里的「令牌」只是抽象的限流额度，不会发给浏览器。

## 继续学习的顺序

| 阶段 | 先解决的问题 | 本地代码 | 公开参考 |
| --- | --- | --- | --- |
| 1. 基础业务 | 登录后如何识别用户？券、订单有哪些字段？ | `UserController`、`UserServiceImpl`、`VoucherOrderController`、`sql/` | [项目介绍](https://javaup.chat/hmdp-plus/overview/project-intro/)、[原仓库 README](https://github.com/java-up-up/hmdp-plus)、[数据库表关系](https://javaup.chat/hmdp-plus/basics/db-relations/) |
| 2. 入口防护 | 为什么先限流、再校验一次性令牌？ | `VoucherOrderController`、`RedisRateLimitHandler`、`tokenBucket.lua` | [限流专题目录](https://javaup.chat/hmdp-plus/token-and-antibot/token-bucket/)、[令牌签发](https://javaup.chat/hmdp-plus/token-and-antibot/token-issuing/) |
| 3. Redis 预扣 | 怎样避免超卖和重复购买？ | `VoucherOrderServiceImpl#doSeckillVoucherV2`、`seckillVoucher.lua` | [异步秒杀概览](https://javaup.chat/hmdp-plus/coupon-flash-sale/async-reliability-refactor/) |
| 4. 异步落库 | 为什么返回 ID 后订单还可能没创建？ | `SeckillVoucherConsumer`、`createVoucherOrderV2`、消息框架 | [异步秒杀概览](https://javaup.chat/hmdp-plus/coupon-flash-sale/async-reliability-refactor/)、[Kafka 生产者](https://javaup.chat/hmdp-plus/components/kafka-producer/) |
| 5. 一致性 | Kafka/数据库失败时怎么修正 Redis？ | `RedisVoucherData`、`ReconciliationTaskServiceImpl` | [对账与补偿](https://javaup.chat/hmdp-plus/ops-consistency/redis-db-compare-compensation/) |
| 6. 数据与缓存 | 热点数据、穿透、击穿、分片分别解决什么？ | `ShopServiceImpl`、`CacheClient`、`shardingsphere.yaml` | [缓存击穿](https://javaup.chat/hmdp-plus/cache-architecture/cache-breakdown-part1/)、[多级缓存](https://javaup.chat/hmdp-plus/cache-architecture/multi-level-cache/)、[分库分表](https://javaup.chat/hmdp-plus/sharding/overview/) |
| 7. 基础组件 | ID、锁、幂等、延迟队列为什么独立成模块？ | `citylife-id-generator-framework`、`citylife-redisson-framework`、`citylife-sharding` | [分布式 ID](https://javaup.chat/hmdp-plus/components/distributed-id/)、[雪花算法](https://javaup.chat/hmdp-plus/tech-essentials/snowflake/)、[幂等组件](https://javaup.chat/hmdp-plus/components/idempotent-component/) |

这张表是**阅读索引，不保证每篇外部文章免费全文可读**。原站导航还覆盖关注、探店、GEO、签到、UV、订阅通知、券取消、Redis 键、过期淘汰、布隆过滤器、Spring 事务等。需要按主题扩展时，从[项目介绍页的目录](https://javaup.chat/hmdp-plus/overview/project-intro/)进入即可。它的目录是知识地图，不能代替对本地实现的核对。

### 扩展专题速查

| 想学什么 | 先问自己 | 当前代码入口 |
| --- | --- | --- |
| 登录与会话 | 验证码校验后，后续请求如何找到当前用户？ | `UserController`、`UserServiceImpl`、登录拦截器 |
| 探店与关注 | 点赞/关注关系存在哪里？列表如何按用户过滤或排序？ | `BlogController`、`BlogServiceImpl`、`FollowServiceImpl` |
| 附近商户 | 经纬度如何变成半径内的商户列表？ | `ShopServiceImpl` 中的 Redis GEO 查询 |
| 签到 | 如何用一个月的位图表示每天是否签到？ | `UserServiceImpl#sign`、`signCount` |
| 缓存防护 | 不存在的商户 ID 为什么不应每次打到数据库？ | `ShopServiceImpl`、`CacheClient`、布隆过滤器模块 |
| 订阅与通知 | 没抢到券的用户如何登记？补发后谁接收通知？ | `VoucherServiceImpl#subscribe`、`AutoIssueNotifyServiceImpl` |
| 锁与幂等 | 重试和并发请求会不会重复写订单？ | `createVoucherOrderV2`、Redisson/幂等组件 |
| 取消与对账 | 取消订单后 Redis 库存与数据库库存如何重新对齐？ | `VoucherOrderServiceImpl#cancel`、`ReconciliationTaskServiceImpl` |
| 分库分表 | 一条订单究竟落到哪个库、哪个表？ | `citylife-core-service/src/main/resources/shardingsphere.yaml` |
| Redis 运维 | 哪些键会过期？重建缓存时怎样避免集中回源？ | `RedisKeyManage`、`CacheClient`，再查原站的 Redis 键专题 |

这份速查只指出代码入口；每个专题的正确性和实际运行表现仍需沿调用链验证。原站还讨论延迟队列、消息死信与指标报警，可从[架构组件目录](https://javaup.chat/hmdp-plus/overview/project-intro/)进入，学到相关业务时再看。

## 动手验证：每次只问一个问题

1. **Lua**：给 `seckillVoucher.lua` 画一张表，列出每个失败返回码发生时，库存、用户集合、追踪日志是否变化。重点验证「同一用户请求两次」。
2. **雪花 ID**：在 `nextId()` 看移位表达式。思考两个实例若拿到相同节点编号，在同一毫秒、同一序号下会怎样；再去看 Redis 分配脚本如何处理上限。
3. **令牌桶**：设容量为 3、3 秒补满，纸上模拟第 0 秒连发 4 次、第 1 秒再发 1 次。再对照 `tokens + delta * ratePerMs`。
4. **异步一致性**：假设 Redis 已扣减但 Kafka 消息没成功发出，沿生产者失败处理、回滚与对账路径找答案；不要只凭「最终一致性」四个字猜。

建议每个主题最后能自己用四句话讲清：**要解决什么问题；数据原先在哪；代码使数据发生了什么变化；失败时如何发现和处理。**

## 简历与项目亮点怎么用

[作者的简历参考](https://javaup.chat/hmdp-plus/overview/resume-template/)列出限流、动态访问令牌、异步秒杀、缓存、分片、对账等候选亮点，但它是参考话术，不是你已经实现和验证过的个人经历。现在最稳妥的写法是先选自己确实读懂且能演示的 1-2 条，例如「Redis Lua 预扣 + Kafka 异步落库」或「令牌桶限流 + 一次性访问令牌」，说明取舍、失败路径和自己做过的改动。像“百万并发”“绝对不会丢单”“完全一致”这类结论，只有测量和故障验证后才能写。

## 资料范围与核对原则

- [原作者项目介绍](https://javaup.chat/hmdp-plus/overview/project-intro/)与[原仓库 README](https://github.com/java-up-up/hmdp-plus)用于了解项目声称覆盖的能力；作者站的 Plus 章节有付费限制，未获取的正文不在本指南范围内。
- 上面的代码路径指向**当前 CityLife 工作区**，而不是原仓库；当前工作区改过项目和包名，也可能与网站版本存在差异。
- 外部页面写的是方案或目标，代码能证明的是当前实现；运行效果、极限吞吐、故障恢复能力还需要测试验证。
