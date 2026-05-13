# Kafka 在黑马点评项目里的落地整理

## 1. 文档目标

这份文档不是单纯讲 Kafka API，而是结合当前 `hmdp` 项目，说明：

- 为什么这个项目适合接 Kafka
- 当前已经接了什么
- 后续哪些业务最适合继续接 Kafka
- 学习时应该重点关注哪些设计点

适合阅读对象：

- 正在学习消息队列
- 已经会基础的 `KafkaTemplate` / `@KafkaListener`
- 想理解 Kafka 在真实业务项目里到底怎么用

---

## 2. 当前项目现状

当前项目已经具备两类典型的消息化基础：

### 2.1 秒杀订单链路

秒杀下单已经做了异步化思路，核心代码在：

- `src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java`
- `src/main/java/com/hmdp/listener/SeckillOrderListener.java`

这条链路的核心目标是：

- 高并发入口快速返回
- 异步落库
- 避免秒杀请求直接把数据库压垮

### 2.2 博客发布链路

博客发布已经接了 Kafka，核心代码在：

- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- `src/main/java/com/hmdp/messaging/BlogEventProducer.java`
- `src/main/java/com/hmdp/messaging/BlogPublishedConsumer.java`
- `src/main/java/com/hmdp/event/BlogPublishedEvent.java`

这条链路的核心目标是：

- 主线程只负责写博客
- 粉丝 feed 分发异步处理
- 降低发布接口耗时

---

## 3. 为什么这个项目适合使用 Kafka

这个项目里很多操作都有一个共同特点：

> 主业务很短，但主业务完成后还有一串后处理动作。

比如“发布博客”：

主链路只需要：

- 写 `tb_blog`

但后面可能还要：

- 推送到粉丝收件箱
- 发通知
- 做统计
- 触发推荐
- 做审计日志

再比如“点赞博客”：

主链路只需要：

- 更新数据库点赞数
- 更新 Redis 点赞状态

但后面也可能还要：

- 通知作者
- 更新热度
- 更新用户画像
- 埋点统计

像这种“后置动作很多”的场景，很适合 Kafka。

Kafka 的价值主要体现在：

- 异步解耦
- 削峰填谷
- 提高主接口响应速度
- 支持多个下游独立消费
- 为后续扩展推荐、通知、统计系统留出空间

---

## 4. Kafka 在这个项目中承担的角色

### 4.1 异步解耦

生产者只负责发事件，不直接关心所有后续处理。

例如：

- `blog.published` 由博客服务发出
- feed 消费者、通知消费者、统计消费者分别独立处理

### 4.2 削峰填谷

在秒杀、订单、热点互动场景中，Kafka 可以把瞬时高峰流量摊平。

请求高峰不再直接压到数据库，而是先进入消息队列，再由消费者按系统可承受节奏处理。

### 4.3 业务扩展点

当后续需求增加时，不需要不断修改主业务方法，而是新增消费者即可。

例如：

- 今天只有 feed 分发
- 明天要加通知
- 后天要加推荐画像

这些都可以在 Kafka 消费端扩展。

---

## 5. 哪些业务最适合继续接 Kafka

下面按优先级列出当前项目最适合继续接 Kafka 的业务点。

### 5.1 博客点赞：`blog.liked`

当前点赞逻辑在：

- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`

推荐拆出的后处理包括：

- 通知博客作者
- 更新博客热度
- 记录用户行为画像
- 做运营统计

推荐 topic：

```text
blog.liked
```

这是最推荐继续落地的下一个 Kafka 场景。

### 5.2 用户关注：`user.followed`

当前关注逻辑在：

- `src/main/java/com/hmdp/service/impl/FollowServiceImpl.java`

推荐拆出的后处理包括：

- 通知被关注用户
- 回填最近 N 条博客到当前用户 feed
- 更新社交图谱
- 更新画像和推荐因子

推荐 topic：

```text
user.followed
```

### 5.3 订单创建 / 支付成功：`voucher.order.created`

当前秒杀下单链路已经有异步基础，但可以继续扩展更规范的事件链路。

推荐拆出的后处理包括：

- 通知
- 销量统计
- 营销发券
- 审计日志

推荐 topic：

```text
voucher.order.created
voucher.order.paid
```

### 5.4 店铺更新：`shop.updated`

当前店铺更新逻辑在：

- `src/main/java/com/hmdp/service/impl/ShopServiceImpl.java`

推荐拆出的后处理包括：

- 多节点缓存刷新
- 搜索索引更新
- GEO 数据重建
- 审计与统计

推荐 topic：

```text
shop.updated
```

### 5.5 用户行为总线：`user.behavior`

把多个用户行为统一抽象成行为流，例如：

- 浏览店铺
- 点赞博客
- 关注用户
- 购买优惠券

推荐 topic：

```text
user.behavior
```

这个场景更偏架构升级，适合在前面几个单事件 topic 熟悉后再做。

---

## 6. 推荐的学习和落地顺序

不建议一口气把所有业务都 Kafka 化，建议按下面顺序推进：

### 第一阶段：吃透已经接入的 `blog.published`

先彻底理解：

- 为什么要 `afterCommit`
- 为什么消费者更适合做 feed fan-out
- 生产者、消费者、topic、事件对象分别扮演什么角色

### 第二阶段：落地 `blog.liked`

这是最适合作为下一个练手场景的点，因为：

- 主链路清晰
- 异步后处理多
- 容易练多个消费者
- 幂等问题很典型

### 第三阶段：落地 `user.followed`

这个阶段可以练：

- 事件驱动的 feed 回填
- 通知场景
- 社交关系事件化

### 第四阶段：订单事件化

适合练：

- 可靠消息
- 幂等
- 重试
- 死信队列

### 第五阶段：统一行为流

适合练：

- 事件驱动架构
- 用户画像
- 推荐系统数据源设计

---

## 7. 不适合硬上 Kafka 的地方

不是所有地方都适合用 Kafka。

当前项目里这些地方不建议为了“练 Kafka”而强接：

- 普通读接口，比如 `queryShopById`
- 登录校验、验证码发送这种短同步链路
- 没有后置动作的轻量写操作

判断标准很简单：

> 如果一个操作就只有一步，而且必须立即返回最终结果，那通常不值得强行上 Kafka。

---

## 8. 统一设计建议

如果后续继续扩展，建议统一下面这些规范。

### 8.1 Topic 命名

建议统一用领域事件命名：

```text
blog.published
blog.liked
user.followed
voucher.order.created
voucher.order.paid
shop.updated
user.behavior
```

### 8.2 包结构

建议统一：

```text
com.hmdp.event
com.hmdp.messaging
```

如果事件和消费者很多，也可以继续拆：

```text
com.hmdp.messaging.producer
com.hmdp.messaging.consumer
```

### 8.3 发送时机

所有“表示事实已经发生”的事件，建议遵循一个原则：

> 数据库事务提交成功后再发送。

当前项目里的 `blog.published` 就已经是这样设计的。

---

## 9. 学习 Kafka 时最重要的能力

如果你是学习阶段，建议重点掌握以下几个能力，而不是只记 API。

### 9.1 事件建模

你要能回答：

- 这个事件的名字应该是什么
- 这个事件表达的业务事实是什么
- 最小字段集应该有哪些

### 9.2 幂等

消费重复消息时，业务不能重复执行。

### 9.3 重试

消费失败时，如何保证系统能自动恢复。

### 9.4 解耦

主业务和后处理边界如何划分。

### 9.5 扩展

如何让新增需求通过“加消费者”而不是“改主流程”完成。

---

## 10. 总结

结合当前黑马点评项目，最适合继续接 Kafka 的业务点是：

1. `blog.liked`
2. `user.followed`
3. `voucher.order.created`
4. `shop.updated`
5. `user.behavior`

推荐学习顺序是：

```text
blog.published
-> blog.liked
-> user.followed
-> voucher.order.created
-> user.behavior
```

一句话总结：

> 在这个项目里，Kafka 最适合放在“主业务完成后还有很多异步后处理”的地方，用来做异步解耦、削峰填谷和可扩展的事件驱动设计。
