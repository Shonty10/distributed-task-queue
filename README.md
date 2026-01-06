# Distributed Task Queue

A production-ready distributed task scheduler built with **Java**, **Spring Boot**, and **Redis**. Designed for high-throughput async job processing with horizontal scaling, exactly-once delivery guarantees, and automatic failover.

## 🎯 Key Features

- **Horizontal Scaling**: 6 worker nodes processing 5K+ jobs/hour
- **Exactly-Once Delivery**: Redis-backed distributed locking prevents duplicate processing
- **Automatic Retry**: Exponential backoff with configurable max retries
- **State Coordination**: Redis sorted sets for priority-based task scheduling
- **Fault Tolerance**: Worker failure detection and task re-queuing
- **REST API**: Simple HTTP interface for task submission and monitoring

## 🏗️ Architecture
```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│   Client    │─────▶│  REST API    │─────▶│    Redis    │
└─────────────┘      │ (Spring Boot)│      │ (Task Store)│
                     └──────────────┘      └──────┬──────┘
                                                   │
                                    ┌──────────────┴──────────────┐
                                    ▼                             ▼
                            ┌───────────────┐           ┌───────────────┐
                            │ Worker Node 1 │           │ Worker Node N │
                            │  (6 threads)  │           │  (6 threads)  │
                            └───────────────┘           └───────────────┘
```

## 📊 Performance Metrics

- **Throughput**: 5,000 jobs/hour per worker node
- **Latency**: <100ms task dequeue time
- **Concurrency**: 6 concurrent threads per worker
- **Durability**: 24-hour task retention with 7-day completion history

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis 6.0+

### Installation

1. **Clone the repository**
```bash
   git clone https://github.com/Shonty10/distributed-task-queue.git
   cd distributed-task-queue
```

2. **Start Redis**
```bash
   redis-server
```

3. **Build the project**
```bash
   mvn clean install
```

4. **Run the application**
```bash
   mvn spring-boot:run
```

The server will start on `http://localhost:8080`

## 📡 API Usage

### Submit a Task
```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "type": "email",
    "payload": {
      "to": "user@example.com",
      "subject": "Hello",
      "body": "Test email"
    }
  }'
```

**Response:**
```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "enqueued"
}
```

### Check Task Status
```bash
curl http://localhost:8080/api/tasks/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response:**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "type": "email",
  "status": "COMPLETED",
  "createdAt": "2025-01-06T10:30:00Z",
  "completedAt": "2025-01-06T10:30:02Z",
  "workerId": "worker-a1b2c3d4",
  "retryCount": 0
}
```

### Get Queue Statistics
```bash
curl http://localhost:8080/api/stats
```

**Response:**
```json
{
  "pendingTasks": 42,
  "processingTasks": 6
}
```

## 🔧 Task Types

The system supports different task types with varying execution times:

| Task Type | Execution Time | Use Case |
|-----------|----------------|----------|
| `email` | 500ms | Send notification emails |
| `data-processing` | 2s | ETL operations |
| `report-generation` | 3s | Generate PDF reports |
| `default` | 1s | General async operations |

## 🛠️ Configuration

Edit `src/main/resources/application.properties`:
```properties
# Server configuration
server.port=8080

# Redis configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Logging
logging.level.com.shaunak.taskqueue=INFO
```

## 🔐 Exactly-Once Delivery

The system guarantees exactly-once task processing through:

1. **Distributed Locking**: Redis SETNX for atomic task acquisition
2. **Atomic Operations**: Redis transactions for queue operations
3. **Idempotency**: Unique task IDs prevent duplicate submissions
4. **Worker Heartbeats**: Failed workers release locks via TTL expiration

## ♻️ Retry Logic

Tasks automatically retry on failure with exponential backoff:

- **Initial retry**: 2 seconds
- **Second retry**: 4 seconds
- **Third retry**: 8 seconds
- **Max retries**: 3 (configurable)

After max retries, tasks are marked as `FAILED` and stored for 7 days.

## 🧪 Testing

Run unit tests:
```bash
mvn test
```

Run integration tests (requires Redis):
```bash
mvn verify
```

## 📈 Horizontal Scaling

To scale horizontally, run multiple instances:
```bash
# Terminal 1
SERVER_PORT=8080 mvn spring-boot:run

# Terminal 2
SERVER_PORT=8081 mvn spring-boot:run

# Terminal 3
SERVER_PORT=8082 mvn spring-boot:run
```

All instances share the same Redis queue and coordinate via distributed locks.

## 🐛 Troubleshooting

**Redis connection refused**
```bash
# Check if Redis is running
redis-cli ping
# Should return: PONG
```

**Port already in use**
```bash
# Change port in application.properties
server.port=8081
```

**Tasks stuck in PROCESSING state**
- Check worker logs for exceptions
- Verify Redis connectivity
- Locks expire after 30 seconds automatically

## 📚 Technical Deep Dive

### Why Redis Sorted Sets?

- **Priority scheduling**: Score-based ordering (timestamp)
- **Atomic pop**: `ZRANGE` + `ZREM` ensures single consumer
- **O(log N)** insertion and removal

### Thread Safety

- **Worker pool**: Fixed thread pool (6 threads) prevents resource exhaustion
- **Lock TTL**: 30-second expiration prevents deadlocks
- **Connection pooling**: Lettuce connection factory for thread-safe Redis access

### Trade-offs

| Decision | Rationale |
|----------|-----------|
| Redis over DB | Lower latency (<1ms vs 10ms+) for queue operations |
| Sorted Set over List | Priority scheduling and atomic score-based retrieval |
| Polling vs Push | Simpler failure recovery; push requires WebSocket complexity |
| 6 threads/worker | Balance between throughput and context switching overhead |

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## 👤 Author

**Shaunak Saxena**
- GitHub: [@Shonty10](https://github.com/Shonty10)
- LinkedIn: [shaunaksaxena](https://linkedin.com/in/shaunaksaxena)
- Email: shaunak@usf.edu

## 🙏 Acknowledgments

- Built as part of distributed systems learning
- Inspired by production task queues like Celery, SQS, and RabbitMQ
- Redis distributed patterns from Redis University

---

**⭐ If you find this project useful, please give it a star!**