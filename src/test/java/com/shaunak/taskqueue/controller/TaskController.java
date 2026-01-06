package com.shaunak.taskqueue.controller;

import com.shaunak.taskqueue.model.Task;
import com.shaunak.taskqueue.service.TaskQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    
    private final TaskQueueService queueService;
    
    public TaskController(TaskQueueService queueService) {
        this.queueService = queueService;
    }
    
    @PostMapping
    public ResponseEntity<Map<String, String>> createTask(@RequestBody TaskRequest request) {
        try {
            Task task = new Task(request.getType(), request.getPayload());
            String taskId = queueService.enqueueTask(task);
            
            return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "enqueued"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable String taskId) {
        try {
            Task task = queueService.getTask(taskId);
            
            if (task == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<TaskQueueService.QueueStats> getStats() {
        return ResponseEntity.ok(queueService.getStats());
    }
    
    public static class TaskRequest {
        private String type;
        private Map<String, Object> payload;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }
}