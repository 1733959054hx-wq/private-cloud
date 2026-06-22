CREATE TABLE IF NOT EXISTS `doc_progress_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `file_id` bigint(20) NOT NULL COMMENT '文件ID',
  `progress_type` tinyint(4) NOT NULL COMMENT '进度类型：1-文档页码(PDF/Word/PPT)，2-音视频播放秒数',
  `progress_value` double NOT NULL DEFAULT '0' COMMENT '具体的进度值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `file_id`) USING BTREE COMMENT '联合唯一索引：一个用户对一个文件只保留一条最新进度'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文件阅读与播放进度表';
