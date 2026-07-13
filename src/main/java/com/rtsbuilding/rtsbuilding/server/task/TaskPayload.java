package com.rtsbuilding.rtsbuilding.server.task;

/**
 * 任务类型自有数据的标记接口。
 *
 * <p>它只保存执行器需要的数据，不能复制进度、暂停和错误等 TaskRecord 已拥有的状态。</p>
 */
public interface TaskPayload {
}
