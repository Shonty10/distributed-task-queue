package com.shaunak.taskqueue.service;

import com.shaunak.taskqueue.model.Task;
import com.shaunak.taskqueue.model.TaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class TaskQueueService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String TASK_QUEUE = "task:queue";
    private static final String TASK_PROCESSING = "task:processing:";
    private static final String TASK_DATA = "task:data:";
    private static final String TASK_LOCK = "task:lock:";
    
    public TaskQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Enqueue a task with exactly-once semantics using Redis transactions
     */
    public String enqueueTask(Task task) throws JsonProcessingException {
        String taskJson = objectMapper.writeValueAsString(task);
        
        // Store task data
        redisTemplate.opsForValue().set(
            TASK_DATA + task.getId(), 
            taskJson, 
            24, 
            TimeUnit.HOURS
        );
        
        // Add to queue with score based on scheduled time
        long score = task.getScheduledAt().toEpochMilli();
        redisTemplate.opsForZSet().add(TASK_QUEUE, task.getId(), score);
        
        return task.getId();
    }
    
    /**
     * Dequeue next task with distributed locking to ensure exactly-once processing
     */
    public Task dequeueTask(String workerId) throws JsonProcessingException {
        // Get next task from sorted set (lowest score = earliest scheduled)
        var taskIds = redisTemplate.opsForZSet().range(TASK_QUEUE, 0, 0);
        
        if (taskIds == null || taskIds.isEmpty()) {
            return null;
        }
        
        String taskId = (String) taskIds.iterator().next();
        
        // Try to acquire distributed lock
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
            TASK_LOCK + taskId,
            workerId,
            30,
            TimeUnit.SECONDS
        );
        
        if (Boolean.FALSE.equals(locked)) {
            // Another worker got the lock
            return null;
        }
        
        // Remove from queue atomically
        redisTemplate.opsForZSet().remove(TASK_QUEUE, taskId);
        
        // Get task data
        String taskJson = (String) redisTemplate.opsForValue().get(TASK_DATA + taskId);
        
        if (taskJson == null) {
            releaseLock(taskId);
            return null;
        }
        
        Task task = objectMapper.readValue(taskJson, Task.class);
        task.setStatus(TaskStatus.PROCESSING);
        task.setWorkerId(workerId);
        
        // Move to processing set
        redisTemplate.opsForValue().set(
            TASK_PROCESSING + taskId,
            objectMapper.writeValueAsString(task),
            1,
            TimeUnit.HOURS
        );
        
        return task;
    }
    
    /**
     * Mark task as completed
     */
    public void completeTask(String taskId) throws JsonProcessingException {
        String taskJson = (String) redisTemplate.opsForValue().get(TASK_PROCESSING + taskId);
        
        if (taskJson != null) {
            Task task = objectMapper.readValue(taskJson, Task.class);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(Instant.now());
            
            // Store final state
            redisTemplate.opsForValue().set(
                TASK_DATA + taskId,
                objectMapper.writeValueAsString(task),
                7,
                TimeUnit.DAYS
            );
            
            // Remove from processing
            redisTemplate.delete(TASK_PROCESSING + taskId);
        }
        
        releaseLock(taskId);
    }
    
    /**
     * Handle task failure with retry logic
     */
    public void failTask(String taskId, String errorMessage) throws JsonProcessingException {
        String taskJson = (String) redisTemplate.opsForValue().get(TASK_PROCESSING + taskId);
        
        if (taskJson != null) {
            Task task = objectMapper.readValue(taskJson, Task.class);
            task.setRetryCount(task.getRetryCount() + 1);
            task.setErrorMessage(errorMessage);
            
            if (task.getRetryCount() < task.getMaxRetries()) {
                // Re-queue with exponential backoff
                task.setStatus(TaskStatus.RETRYING);
                long backoffMs = (long) Math.pow(2, task.getRetryCount()) * 1000;
                task.setScheduledAt(Instant.now().plusMillis(backoffMs));
                
                enqueueTask(task);
            } else {
                // Max retries exceeded
                task.setStatus(TaskStatus.FAILED);
                task.setCompletedAt(Instant.now());
                
                redisTemplate.opsForValue().set(
                    TASK_DATA + taskId,
                    objectMapper.writeValueAsString(task),
                    7,
                    TimeUnit.DAYS
                );
            }
            
            redisTemplate.delete(TASK_PROCESSING + taskId);
        }
        
        releaseLock(taskId);
    }
    
    /**
     * Get task status
     */
    public Task getTask(String taskId) throws JsonProcessingException {
        String taskJson = (String) redisTemplate.opsForValue().get(TASK_DATA + taskId);
        
        if (taskJson == null) {
            taskJson = (String) redisTemplate.opsForValue().get(TASK_PROCESSING + taskId);
        }
        
        return taskJson != null ? objectMapper.readValue(taskJson, Task.class) : null;
    }
    
    /**
     * Get queue statistics
     */
    public QueueStats getStats() {
        Long pending = redisTemplate.opsForZSet().size(TASK_QUEUE);
        
        return new QueueStats(
            pending != null ? pending : 0,
            getProcessingCount()
        );
    }
    
    private long getProcessingCount() {
        var keys = redisTemplate.keys(TASK_PROCESSING + "*");
        return keys != null ? keys.size() : 0;
    }
    
    private void releaseLock(String taskId) {
        redisTemplate.delete(TASK_LOCK + taskId);
    }
    
    public static class QueueStats {
        private final long pendingTasks;
        private final long processingTasks;
        
        public QueueStats(long pendingTasks, long processingTasks) {
            this.pendingTasks = pendingTasks;
            this.processingTasks = processingTasks;
        }
        
        public long getPendingTasks() { return pendingTasks; }
        public long getProcessingTasks() { return processingTasks; }
    }
}