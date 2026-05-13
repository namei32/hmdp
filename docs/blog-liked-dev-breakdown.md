# blog.liked 研发任务拆解文档
> 面向研发执行的任务拆解、联调顺序与验收清单  
> 对应产品需求文档：`docs/blog-liked-prd.md`  
> 对应设计文档：`docs/blog-liked-design.md`

---

## 1. 文档目标

本文档用于把 `blog.liked` 功能从需求文档拆解成可执行的研发任务。

目标是让程序员可以直接按模块推进开发，明确：

- 先做什么
- 后做什么
- 哪些是必须项
- 哪些是可选增强项
- 每一步怎么验收

---

## 2. 交付范围

本次研发交付范围：

1. 点赞/取消点赞主链路保持可用
2. 新增 `blog.liked` Kafka 事件
3. 点赞成功后发送 `LIKE` 事件
4. 取消点赞成功后发送 `UNLIKE` 事件
5. 至少新增一个消费者并成功消费
6. 消费端具备基础幂等
7. 补充最小必要测试
8. 补充日志与文档

本次不强制交付：

- 死信队列完整治理
- outbox
- 后台管理页面
- 完整通知中心

---

## 3. 建议开发顺序

建议按以下顺序推进：

1. 事件模型
2. Topic / Kafka 基础配置
3. 生产者
4. 点赞主流程接入事件发送
5. 消费者
6. 幂等
7. 测试
8. 联调

这样做的原因是：

- 先定义事件边界，避免后面返工
- 先打通最短主链路，再扩消费者
- 幂等放在消费者阶段一起完成，逻辑更集中

---

## 4. 模块级任务拆解

---

## 4.1 事件模型任务

### 目标

定义统一的点赞事件对象，作为生产者和消费者的数据协议。

### 任务项

- 新增事件类 `BlogLikedEvent`
- 定义字段：
  - `eventId`
  - `blogId`
  - `authorId`
  - `userId`
  - `action`
  - `occurredAt`
- 补充无参构造、有参构造、getter/setter
- 统一放到 `com.hmdp.event` 包

### 输出物

- `src/main/java/com/hmdp/event/BlogLikedEvent.java`

### 验收标准

- 事件对象可被序列化为 JSON
- 事件字段命名清晰、无歧义
- 生产端和消费端都能直接引用

---

## 4.2 Topic 与配置任务

### 目标

在 Kafka 中增加 `blog.liked` 主题，并确保项目能正确序列化事件对象。

### 任务项

- 在 topic 常量类中增加：

```text
blog.liked
```

- 在 Kafka 配置类中新增 topic Bean
- 确认当前 JSON 序列化配置对 `com.hmdp.event` 包有效

### 输出物

- `src/main/java/com/hmdp/messaging/KafkaTopics.java`
- `src/main/java/com/hmdp/config/KafkaConfig.java`
- 如有必要，更新 `application.yaml`

### 验收标准

- 应用启动后 topic 可被自动声明
- 事件对象可以正常序列化/反序列化

---

## 4.3 生产者任务

### 目标

封装点赞事件发送逻辑，避免业务代码直接写 KafkaTemplate。

### 任务项

- 新增 `BlogLikeEventProducer`
- 封装统一发送方法：
  - `publish(BlogLikedEvent event)`
- 使用 `blogId` 作为消息 key
- 增加发送成功/失败日志

### 输出物

- `src/main/java/com/hmdp/messaging/BlogLikeEventProducer.java`

### 验收标准

- 调用生产者后可以向 Kafka 成功发送消息
- 发送失败时有明确日志

---

## 4.4 点赞主链路改造任务

### 目标

在不破坏现有点赞功能的基础上，接入 `blog.liked` 事件发送。

### 涉及文件

- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`

### 任务项

- 梳理当前 `likeBlog(Long id)` 方法逻辑
- 在点赞成功分支发送 `LIKE` 事件
- 在取消点赞成功分支发送 `UNLIKE` 事件
- 获取并补充博客作者 id
- 生成唯一 `eventId`
- 补充必要空值保护

### 关键要求

- 只有数据库和 Redis 更新成功后才允许发事件
- 失败时不能误发事件
- 不允许为了发消息而破坏现有点赞逻辑

### 验收标准

- 点赞成功时：
  - 数据库 liked 正确增加
  - Redis 状态正确写入
  - 成功发送 `LIKE` 事件
- 取消点赞成功时：
  - 数据库 liked 正确减少
  - Redis 状态正确移除
  - 成功发送 `UNLIKE` 事件

---

## 4.5 消费者任务

### 目标

至少落地一个真实消费者，打通完整链路。

### 推荐优先级

优先做：

- `BlogLikeNotifyConsumer`

可选后续补充：

- `BlogHotScoreConsumer`
- `UserBehaviorConsumer`

### 本期最小任务项

- 新增 `BlogLikeNotifyConsumer`
- 监听 `blog.liked`
- 仅处理 `action = LIKE`
- 对 `UNLIKE` 先忽略
- 先以日志输出或轻量记录为主

### 输出物

- `src/main/java/com/hmdp/messaging/BlogLikeNotifyConsumer.java`

### 验收标准

- Kafka 收到消息后消费者能正确进入
- 消费者能区分 `LIKE` / `UNLIKE`
- 对 `LIKE` 有实际执行逻辑

---

## 4.6 幂等任务

### 目标

保证消费者重复收到同一事件时，不会产生重复副作用。

### 推荐方案

基于：

- `eventId`
- Redis `SETNX`

做消费幂等控制。

### 任务项

- 设计幂等 key 格式
- 在消费者真正执行业务前做幂等校验
- 幂等命中时直接返回
- 设置合理过期时间

### 推荐 key 形式

```text
mq:idempotent:blog:liked:{consumerName}:{eventId}
```

### 涉及文件

- `BlogLikeNotifyConsumer`
- 如后续有其他消费者，每个消费者都要独立幂等

### 验收标准

- 同一事件重复消费时，不重复执行通知
- 幂等命中时有日志

---

## 4.7 日志任务

### 目标

保证主链路和消息链路都可观察。

### 任务项

- 生产者增加发送成功日志
- 生产者增加发送失败日志
- 消费者增加消费开始日志
- 消费者增加消费完成日志
- 消费者增加幂等命中日志
- 消费者增加异常日志

### 日志字段建议

- `eventId`
- `blogId`
- `userId`
- `authorId`
- `action`

### 验收标准

- 日志足够支撑链路排查
- 失败问题可定位到具体事件

---

## 4.8 测试任务

### 目标

对主链路和消息链路进行最小必要测试覆盖。

### 单元测试建议

- 生产者：
  - 非事务场景立即发送
  - 事务提交后发送

- 消费者：
  - `LIKE` 正常消费
  - `UNLIKE` 忽略或按预期处理
  - 幂等命中时不重复执行

### 业务测试建议

- 点赞成功 -> 事件发送 -> 消费成功
- 取消点赞成功 -> 事件发送
- 重复点赞不重复发错误逻辑
- 未登录用户不能触发事件

### 输出物

- `src/test/java/...`

### 验收标准

- 至少覆盖主链路发送和消费者基础逻辑

---

## 5. 建议的研发拆分方式

如果单人开发，可以按以下阶段推进。

### 阶段 1：打通最短链路

任务：

- 事件对象
- topic 常量
- 生产者
- `likeBlog()` 中发送事件
- 一个打印日志的消费者

阶段目标：

- 从接口到 Kafka 到消费者完整跑通

### 阶段 2：增强可靠性

任务：

- 补幂等
- 补更多日志
- 补单元测试

阶段目标：

- 能抗重复消费

### 阶段 3：扩展下游能力

任务：

- 热度消费者
- 用户行为消费者

阶段目标：

- 形成“一次点赞，多消费者消费”的事件驱动结构

---

## 6. 文件级任务清单

### 必改文件

- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/messaging/KafkaTopics.java`
- `src/main/java/com/hmdp/config/KafkaConfig.java`

### 必增文件

- `src/main/java/com/hmdp/event/BlogLikedEvent.java`
- `src/main/java/com/hmdp/messaging/BlogLikeEventProducer.java`
- `src/main/java/com/hmdp/messaging/BlogLikeNotifyConsumer.java`

### 建议新增测试文件

- `src/test/java/com/hmdp/messaging/BlogLikeEventProducerTest.java`
- `src/test/java/com/hmdp/messaging/BlogLikeNotifyConsumerTest.java`

---

## 7. 联调顺序建议

建议严格按顺序联调：

### 第一步

后端主链路本地验证：

- 点赞成功
- 取消点赞成功

### 第二步

验证消息是否成功发送到 Kafka：

- 看生产者日志
- 看 Kafka topic

### 第三步

验证消费者是否收到：

- 看消费者日志

### 第四步

验证幂等：

- 人工重复投递同一事件
- 确认消费者不重复执行

### 第五步

验证异常分支：

- 模拟消费者异常
- 确认日志可定位

---

## 8. 风险点提醒

### 8.1 事件发送时机错误

如果主业务失败却仍发送了消息，会出现脏事件。

### 8.2 漏查作者 id

如果事件缺少 `authorId`，后续通知消费者还要回表查，增加复杂度。

### 8.3 消费者不做幂等

重复消费会导致通知重复、热度错误、统计不准。

### 8.4 日志过少

链路出问题时无法排查。

### 8.5 主链路侵入过多

不要把太多消费者逻辑重新塞回点赞主方法。

---

## 9. 验收 checklist

### P0 必须完成

- [ ] 新增 `BlogLikedEvent`
- [ ] 新增 `blog.liked` topic 常量和配置
- [ ] 新增 `BlogLikeEventProducer`
- [ ] 点赞成功发送 `LIKE` 事件
- [ ] 取消点赞成功发送 `UNLIKE` 事件
- [ ] 新增至少一个消费者
- [ ] 消费者支持基础幂等
- [ ] 关键日志齐全

### P1 建议完成

- [ ] 增加生产者单元测试
- [ ] 增加消费者单元测试
- [ ] 增加热度消费者雏形

### P2 后续迭代

- [ ] 死信队列
- [ ] outbox
- [ ] 统一行为总线
- [ ] 通知落库

---

## 10. 研发执行 checklist（按模块拆分）

下面这部分可直接作为研发执行清单使用。

### 10.1 接口改造 checklist

- [ ] 确认当前点赞接口沿用现有 Controller，不新增前端接口
- [ ] 梳理 `BlogServiceImpl.likeBlog(Long id)` 当前执行路径
- [ ] 确认点赞与取消点赞的分支条件正确
- [ ] 在点赞成功后增加发送 `LIKE` 事件逻辑
- [ ] 在取消点赞成功后增加发送 `UNLIKE` 事件逻辑
- [ ] 确认只有数据库与 Redis 更新成功后才发事件
- [ ] 确认未登录用户不会进入事件发送逻辑
- [ ] 确认重复点赞不会触发错误事件
- [ ] 确认接口原有返回行为不被破坏

### 10.2 事件对象 checklist

- [ ] 新增 `BlogLikedEvent` 类
- [ ] 增加 `eventId` 字段
- [ ] 增加 `blogId` 字段
- [ ] 增加 `authorId` 字段
- [ ] 增加 `userId` 字段
- [ ] 增加 `action` 字段
- [ ] 增加 `occurredAt` 字段
- [ ] 补充无参构造
- [ ] 补充全参构造
- [ ] 确认事件对象可被 Kafka JSON 序列化

### 10.3 生产者 checklist

- [ ] 新增 `BlogLikeEventProducer`
- [ ] 封装统一发送方法 `publish(BlogLikedEvent event)`
- [ ] 使用 `blog.liked` 作为 topic
- [ ] 使用 `blogId` 作为消息 key
- [ ] 增加发送成功日志
- [ ] 增加发送失败日志
- [ ] 确认发送失败不会破坏主接口返回
- [ ] 确认事件发送代码不直接散落在多个业务类中

### 10.4 消费者 checklist

- [ ] 新增 `BlogLikeNotifyConsumer`
- [ ] 监听 topic `blog.liked`
- [ ] 正确反序列化 `BlogLikedEvent`
- [ ] 能识别 `action = LIKE`
- [ ] 能识别 `action = UNLIKE`
- [ ] 本期对 `LIKE` 至少执行一种实际处理逻辑
- [ ] 本期对 `UNLIKE` 明确处理策略（忽略或预留）
- [ ] 增加消费开始日志
- [ ] 增加消费完成日志
- [ ] 增加异常日志

### 10.5 幂等 checklist

- [ ] 设计幂等 key 格式
- [ ] 基于 `eventId` 实现 Redis `SETNX` 去重
- [ ] 幂等 key 带消费者名，避免多个消费者相互影响
- [ ] 设置合理过期时间
- [ ] 幂等命中时直接返回
- [ ] 幂等命中时输出日志

### 10.6 配置与 Topic checklist

- [ ] 在 `KafkaTopics` 中新增 `BLOG_LIKED`
- [ ] 在 `KafkaConfig` 中声明 `blog.liked` topic
- [ ] 确认 `application.yaml` 中 JSON 反序列化配置包含事件包
- [ ] 确认应用启动后 topic 可创建

### 10.7 测试 checklist

- [ ] 编写生产者测试：发送方法可调用
- [ ] 编写生产者测试：事务后发送逻辑按预期触发
- [ ] 编写消费者测试：`LIKE` 正常消费
- [ ] 编写消费者测试：幂等命中时不重复执行
- [ ] 编写业务测试：点赞成功后事件成功发送
- [ ] 编写业务测试：取消点赞成功后事件成功发送
- [ ] 编写业务测试：未登录用户不发送事件
- [ ] 编写业务测试：重复点赞不会产生错误副作用

### 10.8 联调 checklist

- [ ] 后端服务启动正常
- [ ] Kafka 可连接
- [ ] 点赞接口可调用
- [ ] Kafka 中可看到 `blog.liked` 消息
- [ ] 消费者能收到消息
- [ ] 消费者日志完整
- [ ] 重复消费验证通过
- [ ] 异常分支验证通过

---

## 11. 最终交付定义

当满足以下条件时，可以认定本期研发完成：

1. 点赞/取消点赞功能正常
2. `blog.liked` 事件可正确发送
3. 消费者可正确消费
4. 重复消费不产生重复副作用
5. 有最小测试覆盖
6. 有文档、有日志、可联调

---

## 12. 结论

这份任务拆解的核心目标不是“多写几个类”，而是让研发以最小风险完成：

- 主链路稳定
- 事件链路可用
- 消费链路可扩展

建议执行原则：

> 先打通最短链路，再补幂等和测试，最后扩展更多消费者。
