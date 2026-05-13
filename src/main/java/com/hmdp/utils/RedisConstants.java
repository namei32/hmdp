package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long LOCAL_SHOP_CACHE_TTL = 10L;
    public static final long LOCAL_SHOP_CACHE_MAX_SIZE = 10_000L;

    public static final Long CACHE_VOUCHER_LIST_TTL = 30L;
    public static final Long CACHE_VOUCHER_LIST_TTL_JITTER = 5L;
    public static final String CACHE_VOUCHER_LIST_KEY = "cache:voucher:list:";
    public static final Long LOCAL_VOUCHER_LIST_CACHE_TTL = 5L;
    public static final long LOCAL_VOUCHER_LIST_CACHE_MAX_SIZE = 10_000L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final String LOCK_CACHE_REBUILD_KEY = "lock:cache:rebuild:";
    public static final String SHOP_ID_BLOOM_FILTER_KEY = "shopId";

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
