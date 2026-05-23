package com.hmdp.ai.conversation;

import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ConversationService {

    private static final String CONV_KEY_PREFIX = "cs:conv:";
    private static final String CONV_LIST_PREFIX = "cs:conv:list:";
    private static final long CONV_TTL_SECONDS = 1800; // 30 minutes

    private final StringRedisTemplate stringRedisTemplate;

    public ConversationService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Conversation createConversation(Long userId) {
        String id = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        Conversation conv = Conversation.create(id, userId);
        saveConversation(conv);
        addToUserList(userId, id);
        return conv;
    }

    public Conversation getConversation(String convId) {
        String key = CONV_KEY_PREFIX + convId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        return JSONUtil.toBean(json, Conversation.class);
    }

    public void saveConversation(Conversation conv) {
        String key = CONV_KEY_PREFIX + conv.getId();
        conv.setLastActiveAt(System.currentTimeMillis());
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(conv), CONV_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public void deleteConversation(String convId, Long userId) {
        stringRedisTemplate.delete(CONV_KEY_PREFIX + convId);
        stringRedisTemplate.opsForZSet().remove(CONV_LIST_PREFIX + userId, convId);
    }

    public List<Conversation> getUserConversations(Long userId) {
        String listKey = CONV_LIST_PREFIX + userId;
        Set<String> convIds = stringRedisTemplate.opsForZSet()
                .reverseRange(listKey, 0, 49); // last 50
        if (convIds == null || convIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Conversation> convs = new ArrayList<>();
        for (String convId : convIds) {
            Conversation conv = getConversation(convId);
            if (conv != null) {
                convs.add(conv);
            }
        }
        convs.sort((a, b) -> Long.compare(b.getLastActiveAt(), a.getLastActiveAt()));
        return convs;
    }

    private void addToUserList(Long userId, String convId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(CONV_LIST_PREFIX + userId, convId, System.currentTimeMillis());
    }
}
