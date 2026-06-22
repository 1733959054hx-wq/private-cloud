package front.mq.service;

import front.mq.dto.TaskMessage;

/**
 * 任务处理器接口
 * 每种任务类型对应一个处理器实现
 */
public interface TaskHandler {

    /**
     * 处理任务
     *
     * @param message 任务消息
     * @throws Exception 处理失败时抛出异常，消费者会根据重试策略决定是否重试
     */
    void handle(TaskMessage message) throws Exception;

    /**
     * 支持的任务类型
     */
    String getTaskType();
}
