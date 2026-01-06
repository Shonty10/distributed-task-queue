package com.shaunak.taskqueue.worker;

import com.shaunak.taskqueue.model.Task;
import com.shaunak.taskqueue.service.TaskQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TaskWorker {
    
    private final TaskQueueService queueService;
    private final ExecutorService executorService;
    private final String workerId;
    
    public TaskWorker(TaskQueueService queueService) {
        this.queueService = queueService;
        this.executorService = Executors.newFixedThreadPool(6); // 6 worker threads
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    @Scheduled(fixedRate = 100) // Poll every 100ms
    public void processTasks() {
        try {
            Task task = queueService.dequeueTask(workerId);
            
            if (task != null) {
                executorService.submit(() -> executeTask(task));
            }
        } catch (Exception e) {
            System.err.println("Error polling tasks: " + e.getMessage());
        }
    }
    
    private void executeTask(Task task) {
        try {
            System.out.println("Worker " + workerId + " processing task: " + task.getId());
            
            // Simulate task execution based on type
            switch (task.getType()) {
                case "email":
                    sendEmail(task);
                    break;
                case "data-processing":
                    processData(task);
                    break;
                case "report-generation":
                    generateReport(task);
                    break;
                default:
                    Thread.sleep(1000); // Default 1s task
            }
            
            queueService.completeTask(task.getId());
            System.out.println("Task completed: " + task.getId());
            
        } catch (Exception e) {
            System.err.println("Task failed: " + task.getId() + " - " + e.getMessage());
            try {
                queueService.failTask(task.getId(), e.getMessage());
            } catch (Exception ex) {
                System.err.println("Failed to handle task failure: " + ex.getMessage());
            }
        }
    }
    
    private void sendEmail(Task task) throws InterruptedException {
        Thread.sleep(500); // Simulate email sending
    }
    
    private void processData(Task task) throws InterruptedException {
        Thread.sleep(2000); // Simulate data processing
    }
    
    private void generateReport(Task task) throws InterruptedException {
        Thread.sleep(3000); // Simulate report generation
    }
}