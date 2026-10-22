# CityLife 高并发设计：跟着代码学

> 本文按 [JavaUp 黑马点评 Plus](https://javaup.chat/hmdp-plus/overview/project-intro/) 的专题标题组织，但讲解对象是**当前 CityLife 代码**。网站部分页面只开放标题和前言；这里的内容来自本项目源码核对，是原创教学笔记。阅读顺序建议：先看第 1、3、4 章，能串起一次抢券；再按问题查其他章。

## 先看一张图：一张券如何变成一笔订单

![从排队抢券、Redis 资格检查、消息传递，到数据库保存订单的示意图](images/秒杀请求到订单.png)

图中从左到右可以对应为：用户发请求 → Redis 快速判断库存和一人一单 → Kafka 传递创建订单的消息 → MySQL 保存最终订单。**用户拿到订单号时，最后一步可能还没完成。**这张图是帮助理解的比喻；准确的执行条件以代码为准。

```text
VoucherOrderController
  → VoucherOrderServiceImpl#doSeckillVoucherV2
  → seckillVoucher.lua（Redis 预扣）
  → SeckillVoucherProducer（发 Kafka）
  → SeckillVoucherConsumer（收 Kafka）
  → VoucherOrderServiceImpl#createVoucherOrderV2（写数据库）
```

这里有三份容易混淆的「成功」：

| 阶段 | 成功时已经发生什么 | 还不能说明什么 |
| --- | --- | --- |
| Redis Lua 成功 | 占住库存、登记用户 | 数据库订单已创建 |
| Kafka 发送成功 | Broker 收到消息 | 消费者已处理成功 |
| 数据库事务提交 | 数据库库存和订单已写入 | 其他缓存、通知一定同步完成 |

---

## 一、普通版本讲解：先理解超卖、一人一单和锁

对应网站：[优惠券秒杀](https://javaup.chat/hmdp-plus/basic/coupon-flash-sale/)、[分布式锁](https://javaup.chat/hmdp-plus/basic/distributed-lock/)、[秒杀优化](https://javaup.chat/hmdp-plus/basic/seckill-optimization/)。这些是后面设计的基础。

### 1.1 什么叫超卖

库存只剩 1。用户 A 和 B 都先读到「还有 1 张」，随后都去下单，就可能卖出 2 张。问题在于「读 → 判断 → 修改」被拆开了。

本项目数据库落单时用条件更新：

```sql
UPDATE tb_seckill_voucher
SET stock = stock - 1
WHERE voucher_id = ? AND stock > 0;
```

对应 [createVoucherOrderV2](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherOrderServiceImpl.java)。数据库执行更新时再次判断 `stock > 0`；更新不到记录就失败。它不像 `stock = 之前查到的值` 那样要求库存始终没变化，因此多人抢 100 张券时，能依次成功，直到库存为 0。

### 1.2 什么叫一人一单

「先查有没有订单，再插入」也有同样的并发漏洞：同一用户的两个请求可能都查到没有订单。教程用单机锁、按用户加锁、再讨论事务提交时机。**当前主链并不是靠 `synchronized(userId.intern())` 来解决。**

当前 [seckillVoucher.lua](../citylife-core-service/src/main/resources/lua/seckillVoucher.lua) 在 Redis 一次脚本执行中完成：

```text
读库存 → 检查活动时间/状态 → 检查用户是否在已购 Set
→ Redis 库存减 1 → 把用户加入已购 Set → 记录追踪日志
```

假设原先 `stock=2，已购={}`：用户 11 第一次成功后变为 `stock=1，已购={11}`；同一用户再来，`SISMEMBER` 命中，库存不再减少。数据库落单方法还会查一次同用户同券的正常订单，作为另一层检查。Lua 只让这**一组 Redis 操作**不被其他 Redis 命令插入，不能把 Kafka 和 MySQL 一起变成原子操作。

### 1.3 为什么项目里仍有锁

Redis Lua 用在每次抢券的高频入口；Redisson 锁用在「同一份共享数据不能同时重建或修改」的地方。例如 [SeckillVoucherServiceImpl](../citylife-core-service/src/main/java/com/citylife/service/impl/SeckillVoucherServiceImpl.java) 加载缺失的库存缓存时，按券 ID 拿锁、进锁后再查一次 Redis，只让一个请求从数据库重建。锁不是越大越好：一张券的重建不应堵住所有其他券。

**一句话复述**：抢券入口用 Redis Lua 把资格判断和预扣连成一步；数据库再用 `stock > 0` 条件更新防止最终超卖。

---

## 二、分库分表的设计：ShardingSphere 是什么，本项目怎么用

对应网站：[全面剖析分库分表](https://javaup.chat/hmdp-plus/sharding/overview/)、[具体实现上](https://javaup.chat/hmdp-plus/sharding/impl1/)、[具体实现下](https://javaup.chat/hmdp-plus/sharding/2/)；后两篇会员正文不可见，以下以当前配置和代码为准。

### 2.1 先把 ShardingSphere 想成「分拣员」

订单越来越多时，可以分散到不同数据库和表。业务代码仍操作逻辑表 `tb_voucher_order`；**ShardingSphere-JDBC** 处在 JDBC 访问层，根据配置里的规则决定实际去哪个库、哪张表。它不是另一套存订单的数据库，而是负责路由 SQL 的中间层。

本项目在 [application.yml](../citylife-core-service/src/main/resources/application.yml) 把数据源驱动设为 `ShardingSphereDriver`，指向 [shardingsphere.yaml](../citylife-core-service/src/main/resources/shardingsphere.yaml)。后者定义两个数据库 `ds_0 / ds_1`，以及每类逻辑表的物理节点和分片规则。业务代码中的 `save(voucherOrder)` 不需要手动写 `ds_0` 或 `ds_1`。

### 2.2 一条订单会进哪张表

配置规定 `tb_voucher_order`：**按 `user_id` 选库，按 `voucher_id` 选表**，都是对 2 取模。示例：用户 `11` 买券 `123`，则 `11 % 2 = 1`、`123 % 2 = 1`，这条订单会进入 `ds_1.tb_voucher_order_1`。

```text
逻辑 SQL：INSERT INTO tb_voucher_order (... user_id=11, voucher_id=123 ...)
                                       │
                            ShardingSphere 看分片键
                                       ↓
                       ds_1.tb_voucher_order_1
```

项目的秒杀券表、用户表、用户信息表等也有各自的分片键。**不要把「用了分库分表」理解成所有查询都会更快**：若查询缺少分片键，中间层可能要访问多个分片。

### 2.3 为什么完整订单之外还写 `VoucherOrderRouter`

[VoucherOrder](../citylife-core-service/src/main/java/com/citylife/entity/VoucherOrder.java) 存完整订单；[VoucherOrderRouter](../citylife-core-service/src/main/java/com/citylife/entity/VoucherOrderRouter.java) 只存 `orderId、userId、voucherId` 等定位信息。订单表按用户和券分片，若只给一个 `orderId`，就缺少定位主订单所需的分片键；路由表则按 `order_id` 分片。

当前 [createVoucherOrderV2](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherOrderServiceImpl.java) 同时保存订单和路由记录。但要准确看**目前真正的查询代码**：`getSeckillVoucherOrder` 先按订单号查 60 秒 Redis 缓存，未命中再查路由表；找到后只返回订单号，**没有继续读取完整订单**。路由表在设计上保存了定位主订单的线索，当前这个接口只用它确认订单存在。

**一句话复述**：ShardingSphere 用分片键帮代码选择物理库表；主订单按用户/券分，路由表按订单号分，方便处理只带订单号的查询。

---

## 三、高并发下的缓存体系设计与风险防护

对应网站：[缓存击穿](https://javaup.chat/hmdp-plus/cache-architecture/cache-breakdown-part1/)、[缓存穿透](https://javaup.chat/hmdp-plus/cache-architecture/cache-penetration/)、[多级缓存](https://javaup.chat/hmdp-plus/cache-architecture/multi-level-cache/)、[多级缓存一致性](https://javaup.chat/hmdp-plus/cache-architecture/multi-level-consistency/)。部分 Plus 页面仅有公开预览；本节说明当前代码。

### 3.1 先分清三个问题

| 问题 | 简单例子 | 主要处理方式 |
| --- | --- | --- |
| 穿透 | 一直查根本不存在的券 ID | 布隆过滤器、短期空值缓存 |
| 击穿 | 一张热门券缓存刚过期，万人一起查库 | 按券加锁、进锁后再查一次缓存 |
| 雪崩 | 很多缓存同一时刻过期 | 分散过期时间、预热和回源保护；需按实际配置验证 |

### 3.2 看一次券详情查询，数据经过哪里

[SeckillVoucherServiceImpl#queryByVoucherId](../citylife-core-service/src/main/java/com/citylife/service/impl/SeckillVoucherServiceImpl.java) 的实际路线是：

```text
本地 Caffeine 缓存有？→ 直接返回
否则 Redis 有？→ 放进本地缓存后返回
否则布隆过滤器判定「一定不存在」？→ 拒绝
否则查空值缓存 → 存在则拒绝
否则按券 ID 加 Redisson 锁 → 再查缓存 → 查 MySQL → 回填 Redis 和本地缓存
```

本地缓存是当前服务实例内的「近处抽屉」，Redis 是多个实例共用的「公共仓库」。越靠前读取越快，但缓存副本越多，更新后的同步问题也越大。注意本方法回填的券详情把 `stock` 设为 `null`；**高频变化的库存另用 Redis String 保存**，由 `loadVoucherStock` 装载，不能把券详情缓存里的库存当作实时库存。

商户查询也有具体例子：[ShopServiceImpl#queryByIdV4](../citylife-core-service/src/main/java/com/citylife/service/impl/ShopServiceImpl.java) 用 Redis、布隆、空值缓存、锁和第二次检查；V1/V2/V3 是同类问题的其他实现，当前 `queryById` 选择 V4。

### 3.3 更新券后，其他服务实例怎么知道缓存失效

[VoucherServiceImpl#updateSeckillVoucher](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherServiceImpl.java) 更新数据库后调用 [SeckillVoucherCacheInvalidationPublisher](../citylife-core-service/src/main/java/com/citylife/cache/SeckillVoucherCacheInvalidationPublisher.java)：删除当前实例的本地缓存和 Redis 券相关缓存，再发 Kafka 失效消息。其他实例的 [SeckillVoucherInvalidationConsumer](../citylife-core-service/src/main/java/com/citylife/kafka/consumer/SeckillVoucherInvalidationConsumer.java) 收到后也删除缓存。下次读时再回源重建。

这条链路跨数据库、Redis、Kafka，不是单个数据库事务就能保证三者同一瞬间变化。发送失败路径有重试和 DLQ；当前 [DLQ 消费者](../citylife-core-service/src/main/java/com/citylife/kafka/consumer/SeckillVoucherInvalidationDlqConsumer.java) 主要记录审计信息，不能说成已经自动修复所有缓存失效失败。

**一句话复述**：读时用本地缓存和 Redis 提速，未命中用布隆/空值/锁保护数据库；写时删缓存并通知其他实例也删。

---

## 四、高并发下的优惠券抢购实现与优化

对应网站：[异步秒杀可靠性重构](https://javaup.chat/hmdp-plus/coupon-flash-sale/async-reliability-refactor/)、[可靠生成订单](https://javaup.chat/hmdp-plus/coupon-flash-sale/reliable-ordering-async/)、[超时处理](https://javaup.chat/hmdp-plus/coupon-flash-sale/async-timeout-handling/)、[异常补偿](https://javaup.chat/hmdp-plus/coupon-flash-sale/order-exception-compensation/)；会员正文不可见，本节按代码追踪。

### 4.1 请求线程只做「快速受理」

[VoucherOrderController#seckillVoucher](../citylife-core-service/src/main/java/com/citylife/controller/VoucherOrderController.java) 先限流并校验一次性访问令牌，再调用 `voucherOrderService.seckillVoucher`。当前服务方法明确转到 `doSeckillVoucherV2`；旁边的 V1 是保留的旧路径，读代码别串错。

V2 查询券规则、加载 Redis 库存、验证会员等级，生成 `orderId / traceId`，执行 [seckillVoucher.lua](../citylife-core-service/src/main/resources/lua/seckillVoucher.lua)。脚本成功才组装 `SeckillVoucherMessage`，通过 [SeckillVoucherProducer](../citylife-core-service/src/main/java/com/citylife/kafka/producer/SeckillVoucherProducer.java) 发 Kafka，然后返回 `orderId`。当前调用没有等待发送 future 成功。

例子：`voucherId=123，userId=11，Redis 库存=2`。Lua 成功后 Redis 库存变 1、已购 Set 加入 11、追踪 Hash 写入本次订单；MySQL 此刻可能还没有这笔订单。

### 4.2 消费线程完成「真正落单」

[SeckillVoucherConsumer](../citylife-core-service/src/main/java/com/citylife/kafka/consumer/SeckillVoucherConsumer.java) 的 `@KafkaListener` 收到 JSON，基类 `consumeRaw` 把它还原成消息对象。子类 `beforeConsume` 检查消息是否已超过 10 秒；没超时，`doConsume` 调用 [createVoucherOrderV2](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherOrderServiceImpl.java)。这里先查正常订单，再执行数据库 `stock > 0` 条件扣减，保存主订单、路由和对账日志；正常返回后监听方法手动确认消息。

`@RepeatExecuteLimit(keys={"#message.uuid"})` 限制同一消息 UUID 的重复执行；「同用户同券已有订单」是另一层业务检查。两者解决的问题不同，不能只靠 UUID 去重证明用户永远不会重复下单。

### 4.3 三种失败，数据会怎样变化

| 发生点 | 当前代码做什么 | 为什么需要 |
| --- | --- | --- |
| Kafka 发送失败 | 生产者回调尝试回滚 Redis 预扣 | 不让用户一直被标成「已购」而没有订单 |
| 消息超过 10 秒才消费 | `beforeConsume` 放弃落单、尝试回滚并记对账日志 | 防止过晚的订单继续创建 |
| 数据库落单抛异常 | 消费失败回调尝试回滚 Redis，异常继续向外抛 | DB 与 Redis 状态要重新核对 |

Redis 回滚不是简单 `stock + 1`：[回滚 Lua](../citylife-core-service/src/main/resources/lua/seckillVoucherRollBack.lua) 删除库存缓存键，并按情况处理已购集合与追踪日志，让后续装载库存有机会重新取可信数据。若回滚多次失败，[RedisVoucherData](../citylife-core-service/src/main/java/com/citylife/kafka/redis/RedisVoucherData.java) 留失败日志和告警。**不要把「有回滚代码」等同于「任何故障都能自动恢复」。**

**一句话复述**：请求先在 Redis 抢资格并返回订单号，Kafka 消费者再写 MySQL；中途任何一步失败，都要检查和修正两边的数据。

---

## 五、秒杀的护城河：动态令牌与防刷体系

对应网站：[动态令牌桶](https://javaup.chat/hmdp-plus/token-and-antibot/token-bucket/)、[滑动窗口](https://javaup.chat/hmdp-plus/token-and-antibot/sliding-window/)、[令牌签发](https://javaup.chat/hmdp-plus/token-and-antibot/token-issuing/)；以下按本项目实现解释。

### 5.1 两种「令牌」不要混

令牌桶的令牌是**系统内部的放行名额**，用户看不到。秒杀访问令牌是**发给用户的一次性凭证**，下单请求要带回来。前者控制速度，后者控制是否走过领取资格这一步；两者都不能代替最终库存判断。

### 5.2 项目入口怎样防刷

[VoucherOrderController](../citylife-core-service/src/main/java/com/citylife/controller/VoucherOrderController.java) 有两个入口：先访问 `/seckill/token/{id}` 领取短期访问令牌，再访问 `/seckill/{id}` 提交抢券。两个入口都调用 [RedisRateLimitHandler](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/java/com/citylife/execute/RedisRateLimitHandler.java)；后者按 IP、用户以及场景构造限流键。

默认走 [tokenBucket.lua](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/resources/lua/tokenBucket.lua)：像水桶每隔一段时间补充名额，短时允许一小波请求，通过后扣名额。配置可切到 [rateLimitSliding.lua](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/resources/lua/rateLimitSliding.lua)，改为统计最近一个时间窗口内的请求数。默认参数见 [SeckillRateLimitConfigProperties](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/java/com/citylife/config/SeckillRateLimitConfigProperties.java)，具体部署可覆盖。

[SeckillAccessTokenServiceImpl](../citylife-redis-tool-framework/citylife-redis-rate-limit-framework/src/main/java/com/citylife/service/impl/SeckillAccessTokenServiceImpl.java) 按用户和券在 Redis 存短期随机令牌，默认 30 秒。提交时 Lua 校验并删除，所以同一凭证不能反复用。通过这些入口检查后，请求才走到上一章的 Redis 抢券 Lua。

**一句话复述**：令牌桶挡住过快请求，一次性访问令牌要求用户先取得短期凭证，库存和一人一单仍由秒杀 Lua 最后判断。

---

## 六、到券提醒系统设计与最佳实践

对应网站：[无库存订阅与到券通知](https://javaup.chat/hmdp-plus/restock-notify/subscription-flow/)、[公平分配订阅用户](https://javaup.chat/hmdp-plus/restock-notify/fair-allocation/)；以下按当前代码解释。

### 6.1 没库存时，订阅保存了什么

[VoucherServiceImpl#subscribe](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherServiceImpl.java) 先看用户是否已购买；未购买时把用户写进 Redis Set 去重、ZSet 记录首次订阅时间、Hash 保存订阅状态。ZSet 可以按时间找较早订阅者；Set 让同一用户重复点击不会一再加入。取消订阅会从相应集合移除。

### 6.2 库存回来后，怎样尝试给订阅者

运营增加库存或用户取消订单后，[autoIssueVoucherToEarliestSubscriber](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherOrderServiceImpl.java) 从 ZSet 寻找较早的未购用户，校验人群规则，再执行秒杀 Lua 预扣，成功后发 Kafka 创建订单。**排在前面不代表一定成功**：活动状态、库存、资格、消息与落库都可能失败。当前找到的人若不符合人群规则，本次调用就返回失败；不能称为已实现对所有候选的严格公平轮询。

### 6.3 开抢前提醒与补库存订阅不是一回事

[VoucherServiceImpl#sendDelayedVoucherReminder](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherServiceImpl.java) 计算「距离开抢时间 − 提前提醒秒数」，把消息放入 Redisson 延迟队列。时间到后，[ConsumerDelayedVoucherReminder](../citylife-core-service/src/main/java/com/citylife/delay/consumer/ConsumerDelayedVoucherReminder.java) 依据会员等级和店铺近期 Top 买家找接收者，并用 Redis 去重键减少重复处理。这里的短信/App 分支目前**只写日志，没有真正调用发送接口**。

**一句话复述**：订阅是库存回来时尝试给谁；延迟提醒是开抢前到时间通知谁。它们是两条不同流程。

---

## 七、券务运维与一致性保障

对应网站：[订单取消](https://javaup.chat/hmdp-plus/ops-consistency/order-cancel-cleanup/)、[库存双轨一致性](https://javaup.chat/hmdp-plus/ops-consistency/stock-dual-consistency/)、[并发与锁](https://javaup.chat/hmdp-plus/ops-consistency/concurrency-lock-consistency/)、[Redis/DB 对账](https://javaup.chat/hmdp-plus/ops-consistency/redis-db-compare-compensation/)；会员正文不可见，以下以源码为准。

### 7.1 为什么有 Redis 库存和 MySQL 库存两份

Redis 库存负责在高并发入口快速预扣；MySQL 库存在消费消息后作为订单侧记录。两者更新时刻不同，短暂不一致是异步流程的中间状态。真正危险的是失败后一直不一致：例如 Redis 已扣，Kafka 消息没发出去；或 Kafka 收到，但数据库订单没建成。

### 7.2 取消一单会动哪些地方

[VoucherOrderServiceImpl#cancel](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherOrderServiceImpl.java) 找到当前用户的正常订单，改成取消，写恢复对账日志，增加 MySQL 库存；之后处理 Redis 已购/库存状态、订阅状态和购买排行，并尝试把资格给较早订阅者。这比「订单状态改一下」多得多，因为一次购买在几个系统里留下了数据。

运营改库存由 [VoucherServiceImpl#updateSeckillVoucherStock](../citylife-core-service/src/main/java/com/citylife/service/impl/VoucherServiceImpl.java) 处理：用按券的写锁保护更新，同时调整数据库与 Redis 库存。**锁能减少并发冲突，不会让 MySQL 与 Redis 自动组成一个事务。**

### 7.3 对账到底在对什么

可以把对账理解为「核对三本账」：Redis 的扣减追踪、数据库订单、数据库对账日志。[ReconciliationTaskServiceImpl](../citylife-core-service/src/main/java/com/citylife/service/impl/ReconciliationTaskServiceImpl.java) 找出 Redis 有扣减但 DB 缺相应记录，或 DB 日志存在但 Redis 追踪缺失等情况，执行它明确写好的清理、补写和状态标记。它覆盖的是代码里的具体差异，不能推出所有故障都能自动修复。

**一句话复述**：高并发下允许 Redis 先扣、DB 后写，但要留下追踪证据，失败后能回滚或对账。

---

## 八、架构组件讲解：它们怎样被业务复用

对应网站的 [架构组件目录](https://javaup.chat/hmdp-plus/components/distributed-id/)；其中多数正文有会员限制。本节只说当前项目中真正能找到的组件及调用点。

| 组件 | 先用简单话理解 | 当前代码入口 |
| --- | --- | --- |
| 雪花 ID | 多台服务各自拿到不同编号，再结合时间和序号生成订单号 | [SnowflakeIdGenerator](../citylife-id-generator-framework/src/main/java/com/citylife/toolkit/SnowflakeIdGenerator.java) |
| 分布式锁 | 不同服务实例对同一资源也要排队 | [ServiceLockAspect](../citylife-redisson-framework/citylife-redisson-service-framework/citylife-service-lock-framework/src/main/java/com/citylife/servicelock/aspect/ServiceLockAspect.java) |
| 防重复执行 | 同一消息 UUID 重来时限制重复业务操作 | [RepeatExecuteLimitAspect](../citylife-redisson-framework/citylife-redisson-service-framework/citylife-repeat-execute-limit-framework/src/main/java/com/citylife/repeatexecutelimit/aspect/RepeatExecuteLimitAspect.java) |
| Redis Key 与操作 | 集中定义库存、已购、订阅等 Key 的格式 | [RedisKeyManage](../citylife-redis-tool-framework/citylife-redis-framework/src/main/java/com/citylife/core/RedisKeyManage.java) |
| 布隆过滤器 | 快速排除肯定不存在的券或商户 ID；「可能存在」还要继续查 | [BloomFilterHandler](../citylife-redisson-framework/citylife-redisson-service-framework/citylife-bloom-filter-framework/src/main/java/com/citylife/handler/BloomFilterHandler.java) |
| Kafka 生产/消费基类 | 统一发送回调、消息解析和消费钩子 | [AbstractProducerHandler](../citylife-mq-framework/citylife-mq-producer-framework/src/main/java/com/citylife/AbstractProducerHandler.java)、[AbstractConsumerHandler](../citylife-mq-framework/citylife-mq-consumer-framework/src/main/java/com/citylife/consumer/AbstractConsumerHandler.java) |
| 延迟队列 | 先存消息，到时间再交给消费者执行 | [DelayProduceQueue](../citylife-redisson-framework/citylife-service-delay-queue-framework/src/main/java/com/citylife/core/DelayProduceQueue.java)、[DelayConsumerQueue](../citylife-redisson-framework/citylife-service-delay-queue-framework/src/main/java/com/citylife/core/DelayConsumerQueue.java) |
| 指标与告警 | 回滚失败时留下可追查的信号 | [RedisVoucherData](../citylife-core-service/src/main/java/com/citylife/kafka/redis/RedisVoucherData.java) |

这些组件各解决一类问题，但不要只按类名读。先从业务调用点进入：抢券时跟 Lua 和 Kafka；查券时跟缓存/布隆/锁；提醒时跟延迟队列。

---

## 九、技术精华：三个最容易误解的边界

对应网站：[雪花算法](https://javaup.chat/hmdp-plus/tech-essentials/snowflake/)、[Redisson 锁原理](https://javaup.chat/hmdp-plus/tech-essentials/redisson-lock-principle/)、[Spring 事务失效](https://javaup.chat/hmdp-plus/tech-essentials/spring-transaction-failure/)、[延迟队列原理](https://javaup.chat/hmdp-plus/tech-essentials/redisson-delay-queue/) 等公开专题。

1. **`@Transactional` 管的是数据库事务。** 类内部 `this.method()` 调用通常绕过 Spring 代理；事务也不会顺带回滚 Redis 和 Kafka。看取消订单或缓存失效代码时，特别要注意跨系统边界。
2. **Lua 原子性只在 Redis 内。** 抢券脚本里的判断和扣减可以连成一次执行，但脚本成功后 Kafka 仍可能失败，所以要有回滚与对账。
3. **消息队列有不同时间语义。** 下单 Kafka 消息要尽快消费；开抢提醒的 Redisson `RDelayedQueue` 要等到指定延迟结束才进入阻塞队列。当前 [延迟消费者](../citylife-redisson-framework/citylife-service-delay-queue-framework/src/main/java/com/citylife/core/DelayConsumerQueue.java) 用 `take()` 等消息，再把处理交给线程池。

### 学完后用一笔订单自测

假设用户 `11` 抢券 `123`、Redis 库存原来 `2`。你能沿代码说清下面五个问题，就真正理解了主线：

1. 哪一步让 Redis 库存从 2 变 1，同时记下用户 11？
2. 为什么接口先返回 `orderId`，数据库订单可能还不存在？
3. 哪一步检查消息是否超过 10 秒？超时后怎么处理？
4. 用户 11 的主订单按哪两个字段选择数据库和表？
5. 如果 Redis 扣了但数据库没订单，哪些日志或记录帮助对账？

从[控制器](../citylife-core-service/src/main/java/com/citylife/controller/VoucherOrderController.java)开始，依次点进服务、Lua、生产者、消费者和对账服务；每看一个方法，只记录「收到什么、修改了什么、接下来交给谁」。
