# blog.liked 在当前项目里的落地设计
> 从 topic、事件对象、生产者、消费者到幂等

---

## 1. 目标

把“博客点赞 / 取消点赞”这件事事件化，形成一条新的 Kafka 链路：

```text
blog.liked
```

主业务继续同步完成：

- 更新数据库点赞数
- 更新 Redis 点赞状态

后置动作改成异步处理：

- 通知作者
- 更新热度
- 写行为流水
- 做统计

---

## 2. 当前现状

当前博客点赞逻辑位于：

- `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`

现有逻辑已经能完成点赞主业务：

- 点赞：数据库 `liked + 1`
- 取消点赞：数据库 `liked - 1`
- Redis ZSet 记录用户点赞状态

这已经能支持页面功能，但如果未来继续往这个方法里同步堆：

- 通知
- 热度
- 统计
- 推荐画像

主方法会越来越重，所以适合新增一条 Kafka 事件链路。

---

## 3. 为什么 `blog.liked` 适合接 Kafka

点赞博客有几个典型特点：

### 3.1 主链路短

用户点击点赞后，最关心的是界面能尽快成功返回。

### 3.2 后处理多

点赞后经常还要做很多附加动作：

- 通知博客作者
- 更新博客热度
- 记录用户兴趣画像
- 做运营统计

### 3.3 可以异步

用户不需要等“通知作者”完成后再看到点赞成功。

### 3.4 扩展性强

今天可能只需要通知，明天就可能加热度、画像、风控、推荐。

这就是典型的事件驱动场景。

---

## 4. Topic 设计

推荐 topic：

```text
blog.liked
```

建议点赞和取消点赞共用一个 topic，不要拆成两个。

理由：

- 它们本质上是同一个领域事件的两种动作
- 消费者更容易统一处理
- 扩展更自然

通过 `action` 字段区分：

- `LIKE`
- `UNLIKE`

---

## 5. 事件对象设计

建议放到：

```text
src/main/java/com/hmdp/event
```

推荐定义：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogLikedEvent {
    private String eventId;
    private Long blogId;
    private Long authorId;
    private Long userId;
    private String action; // LIKE / UNLIKE
    private Long occurredAt;
}
```

### 字段说明

#### `eventId`

事件唯一 id，用于消费者幂等处理。

这是强烈建议加上的字段。

#### `blogId`

被点赞博客的 id。

#### `authorId`

博客作者 id。

这个字段建议直接放在事件里，避免消费者再回表查一次作者。

#### `userId`

发起点赞动作的用户 id。

#### `action`

行为类型：

- `LIKE`
- `UNLIKE`

#### `occurredAt`

事件发生时间。

---

## 6. 生产者设计

建议新增类：

```text
src/main/java/com/hmdp/messaging/BlogLikeEventProducer.java
```

职责：

- 接收 `BlogLikedEvent`
- 发往 Kafka
- 打发送成功/失败日志

推荐示意代码：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogLikeEventProducer {

    private final KafkaTemplate<String, BlogLikedEvent> kafkaTemplate;

    public void publish(BlogLikedEvent event) {
        kafkaTemplate.send("blog.liked", String.valueOf(event.getBlogId()), event)
                .addCallback(
                        result -> log.info("published blog liked event, eventId={}", event.getEventId()),
                        ex -> log.error("failed to publish blog liked event, eventId={}", event.getEventId(), ex)
                );
    }
}
```

### 为什么 key 建议用 `blogId`

因为同一篇博客的点赞/取消点赞事件更适合落到同一分区：

- 热度更新更容易保持顺序
- 同一博客的事件处理更聚合

---

## 7. 在哪里发消息

推荐在点赞主业务成功之后发。

也就是：

- 数据库更新成功
- Redis 更新成功
- 再发 Kafka 事件

### 点赞成功时

发：

```text
action = LIKE
```

### 取消点赞成功时

发：

```text
action = UNLIKE
```

### 推荐改造思路

在 `likeBlog(Long id)` 里：

1. 拿当前用户 id
2. 查当前博客是否已点赞
3. 执行数据库和 Redis 更新
4. 只有成功后才发 `blog.liked`

---

## 8. 生产者发送示意

例如：

```java
blogLikeEventProducer.publish(new BlogLikedEvent(
        UUID.randomUUID().toString(),
        blogId,
        authorId,
        userId,
        "LIKE",
        System.currentTimeMillis()
));
```

取消点赞时：

```java
blogLikeEventProducer.publish(new BlogLikedEvent(
        UUID.randomUUID().toString(),
        blogId,
        authorId,
        userId,
        "UNLIKE",
        System.currentTimeMillis()
));
```

---

## 9. 推荐的消费者拆分

不要只做一个“大而全”的消费者，建议按职责拆。

---

## 9.1 通知消费者

推荐类名：

```text
BlogLikeNotifyConsumer
```

职责：

- 当 `action = LIKE` 时，给博客作者发通知
- 当 `action = UNLIKE` 时，可选忽略或做撤销逻辑

学习价值：

- 最容易验证 Kafka 链路是否通
- 最适合先落地

---

## 9.2 热度消费者

推荐类名：

```text
BlogHotScoreConsumer
```

职责：

- 根据点赞/取消点赞更新博客热度
- 可以维护一个 Redis 热度 ZSet

例如：

```text
blog:hot:score
```

学习价值：

- 训练你做“事件驱动的统计更新”
- 理解 `LIKE` 和 `UNLIKE` 对状态的影响

---

## 9.3 用户行为消费者

推荐类名：

```text
UserBehaviorConsumer
```

职责：

- 把点赞行为写入行为流水
- 给画像、推荐系统提供输入

这一步可以先简单日志化，后面再逐步升级成统一行为总线。

---

## 9.4 统计消费者

推荐类名：

```text
BlogLikeAnalyticsConsumer
```

职责：

- 统计点赞次数
- 统计取消点赞次数
- 输出运营分析报表所需数据

---

## 10. 一个简单消费者示意

先以通知消费者为例：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogLikeNotifyConsumer {

    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(topics = "blog.liked", groupId = "hmdp-blog-like-notify")
    public void handle(BlogLikedEvent event) {
        if (!"LIKE".equals(event.getAction())) {
            return;
        }

        String key = "mq:idempotent:blog:liked:notify:" + event.getEventId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(success)) {
            return;
        }

        log.info("notify author, authorId={}, blogId={}, fromUserId={}",
                event.getAuthorId(), event.getBlogId(), event.getUserId());
    }
}
```

---

## 11. 为什么消费者必须做幂等

Kafka 消费不能假设“绝对只消费一次”。

重复消费很常见，原因包括：

- 消费处理后提交 offset 前进程异常
- 网络抖动
- 消费者重平衡
- 重试机制导致重复执行

如果不做幂等，可能出现：

- 给作者发两次通知
- 热度加两次
- 行为流水重复写入
- 报表重复累计

所以消费者必须能接受“同一条事件再来一次”。

---

## 12. 幂等怎么做

### 方案 1：基于 `eventId` + Redis 去重

最适合当前项目，简单直接。

示意：

```java
String key = "mq:idempotent:" + event.getEventId();
Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 24, TimeUnit.HOURS);
if (Boolean.FALSE.equals(ok)) {
    return;
}
```

优点：

- 实现简单
- 适合学习阶段

缺点：

- 依赖 Redis
- 需要设置合理过期时间

### 方案 2：数据库唯一约束

如果落通知表、流水表，可以给 `event_id` 建唯一索引。

优点：

- 持久化级幂等

缺点：

- 侵入数据库表设计

---

## 13. 重试和死信怎么考虑

学习阶段建议分两步：

### 第一步：先跑通主链路 + 幂等

先做到：

- 生产者能发
- 消费者能收
- 消费者幂等

### 第二步：再考虑失败重试

当消费者处理失败时：

- 可以先通过抛异常让容器感知失败
- 再逐步扩展统一错误处理

### 第三步：引入死信队列

如果某条消息：

- 多次重试后还失败
- 且持续阻塞消费链路

那就应该送到死信队列，例如：

```text
blog.liked.dlt
```

这样主链路不会一直被坏消息卡住。

---

## 14. 事件执行顺序

完整链路如下。

### 主线程

1. 用户点击点赞
2. `likeBlog()` 执行
3. 数据库点赞数更新
4. Redis 点赞状态更新
5. 发 `blog.liked`
6. 接口返回

### Kafka 消费线程

1. `BlogLikeNotifyConsumer` 收到消息
2. `BlogHotScoreConsumer` 收到消息
3. `UserBehaviorConsumer` 收到消息
4. `BlogLikeAnalyticsConsumer` 收到消息

也就是说：

> 主流程只负责“点赞成功”，附加动作全部异步化。

---

## 15. 推荐的落地顺序

建议按下面顺序推进。

### 第一步

新增：

- `BlogLikedEvent`
- `BlogLikeEventProducer`

### 第二步

在 `likeBlog()` 成功后发事件

### 第三步

先只实现一个消费者：

- `BlogLikeNotifyConsumer`

先只打日志，验证链路是否打通。

### 第四步

再加第二个消费者：

- `BlogHotScoreConsumer`

### 第五步

给消费者补幂等

### 第六步

最后再考虑：

- 重试
- 死信
- 监控

---

## 16. 推荐目录结构

建议统一如下：

```text
src/main/java/com/hmdp/event/BlogLikedEvent.java

src/main/java/com/hmdp/messaging/BlogLikeEventProducer.java
src/main/java/com/hmdp/messaging/BlogLikeNotifyConsumer.java
src/main/java/com/hmdp/messaging/BlogHotScoreConsumer.java
src/main/java/com/hmdp/messaging/UserBehaviorConsumer.java
src/main/java/com/hmdp/messaging/BlogLikeAnalyticsConsumer.java
```

---

## 17. 总结

`blog.liked` 是当前项目里最适合继续练习 Kafka 的下一个业务点，因为它同时具备：

- 主链路短
- 后处理多
- 易于异步化
- 多消费者扩展空间大
- 幂等问题典型

一句话总结：

> `blog.liked` 不是为了“多一个 topic”，而是为了把点赞成功后的通知、热度、画像、统计等动作从主链路里拆出来，做成一条真正可扩展的事件驱动链路。
