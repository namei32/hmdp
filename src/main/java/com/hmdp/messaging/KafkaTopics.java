package com.hmdp.messaging;

public final class KafkaTopics {

    public static final String BLOG_PUBLISHED = "blog.published";
    public static final String BLOG_PUBLISHED_DLT = "blog.published.dlt";
    public static final String BLOG_LIKED = "blog.liked";
    public static final String BLOG_LIKED_DLT = "blog.liked.dlt";
    public static final String SECKILL_ORDER = "seckill.order";
    public static final String SECKILL_ORDER_DLT = "seckill.order.dlt";
    private KafkaTopics() {
    }
}
