CREATE TABLE IF NOT EXISTS `message_outbox` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `topic` varchar(128) NOT NULL COMMENT 'Kafka topic',
  `message_key` varchar(128) NOT NULL COMMENT 'Kafka message key',
  `payload` longtext NOT NULL COMMENT '消息 JSON',
  `status` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT 'INIT/SENDING/SENT/FAILED',
  `retry_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_next_retry_time` (`status`, `next_retry_time`) USING BTREE,
  INDEX `idx_topic_message_key` (`topic`, `message_key`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;
