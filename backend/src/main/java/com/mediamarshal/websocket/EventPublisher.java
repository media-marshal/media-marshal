package com.mediamarshal.websocket;

import com.mediamarshal.model.entity.MediaTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WebSocket 事件推送组件
 *
 * 所有需要实时通知前端的事件都通过此类发布。
 * 前端订阅 /topic/tasks 频道接收任务状态变化推送。
 *
 * 事件类型：
 *   - task.created       新任务入队
 *   - task.processing    开始处理
 *   - task.confirm       需要人工确认
 *   - task.done          处理完成
 *   - task.failed        处理失败
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private static final String TOPIC_TASKS = "/topic/tasks";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishTaskCreated(MediaTask task) {
        publish("task.created", task);
    }

    public void publishTaskDone(MediaTask task) {
        publish("task.done", task);
    }

    public void publishTaskFailed(MediaTask task) {
        publish("task.failed", task);
    }

    public void publishAwaitingConfirmation(MediaTask task) {
        publish("task.confirm", task);
    }

    private void publish(String eventType, MediaTask task) {
        Map<String, Object> event = Map.of(
                "type", eventType,
                "taskId", task.getId(),
                "status", task.getStatus(),
                "sourcePath", task.getSourcePath()
        );
        log.debug("Publishing event: type={}, taskId={}", eventType, task.getId());
        messagingTemplate.convertAndSend(TOPIC_TASKS, event);
    }
}
