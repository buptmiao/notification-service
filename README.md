# Notification Service

## 问题理解

### 业务场景

企业内部存在多个业务系统（用户系统、订单系统、支付系统等），需要向外部供应商系统（广告系统、CRM 系统、库存系统等）发送通知。每个业务系统单独处理外部 API 调用会面临以下问题：

- **重复建设**：每个系统都需要实现重试、超时、错误处理等逻辑
- **可靠性差**：外部系统不可用时，通知可能丢失
- **难以监控**：分散的调用难以统一监控和告警
- **维护成本高**：外部 API 变更时需要修改多个系统

### 解决方案

提供统一的通知服务，屏蔽外部 API 差异，保证可靠投递，提供统一监控。

### 系统边界

**本系统解决的问题：**
- 统一的通知发送接口，屏蔽不同供应商 API 的差异
- 可靠投递保证（至少一次语义）
- 自动重试与失败处理
- 通知状态追踪与查询
- 按供应商维度的监控指标

**本系统不解决的问题：**
- 外部供应商 API 的速率限制（由供应商自行处理）
- 通知内容的业务逻辑验证（由业务系统负责）
- 外部 API 的响应内容解析（业务系统不关心返回值）
- 通知的幂等性保证（由业务系统通过唯一业务 ID 实现）

## 整体架构

```
┌─────────────────┐     ┌─────────────────────────────────────────────────────┐
│  Business       │     │              Notification Service                    │
│  Systems        │     │                                                      │
│                 │     │  ┌──────────┐    ┌──────────┐    ┌──────────────┐   │
│  ┌───────────┐  │     │  │          │    │          │    │              │   │
│  │ User Svc  │──┼────▶│  │   API    │───▶│ RabbitMQ │───▶│  Delivery    │   │
│  └───────────┘  │     │  │ Controller│    │  Queue   │    │  Worker      │   │
│                 │     │  │          │    │          │    │              │   │
│  ┌───────────┐  │     │  └──────────┘    └──────────┘    └──────┬───────┘   │
│  │ Order Svc │──┼────▶│       │                                  │          │
│  └───────────┘  │     │       │                                  │          │
│                 │     │       ▼                                  ▼          │
│  ┌───────────┐  │     │  ┌──────────┐                     ┌──────────────┐  │
│  │Payment Svc│──┼────▶│  │ MongoDB  │◀────────────────────│   HTTP       │  │
│  └───────────┘  │     │  │          │                     │   Client     │  │
│                 │     │  └──────────┘                     └──────┬───────┘  │
└─────────────────┘     │                                          │          │
                        │                                          ▼          │
                        │                                   ┌──────────────┐  │
                        │                                   │  External    │  │
                        │                                   │  Vendor APIs │  │
                        │                                   └──────────────┘  │
                        └─────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 职责 |
|------|------|
| API Controller | 接收通知请求，返回 202 Accepted |
| NotificationService | 业务逻辑处理，状态管理 |
| NotificationProducer | 发布消息到 RabbitMQ |
| DeliveryWorker | 消费队列消息，执行投递 |
| VendorAdapter | 适配不同外部系统的 API 调用 |
| RetryDelayCalculator | 计算指数退避重试延迟 |

### 组件选型

| 组件 | 选择 | 原因 |
|------|------|------|
| 消息队列 | RabbitMQ | 支持延迟消息（用于重试），成熟稳定 |
| 持久化存储 | MongoDB | 文档模型适合存储灵活的通知数据 |

**不使用时的替代方案：**
- 如果没有 RabbitMQ：可使用数据库轮询 + 定时任务，但会增加数据库压力
- 如果没有 MongoDB：可使用 PostgreSQL，但需要更严格的 schema 设计


## 核心设计

### 投递语义

采用 **至少一次（At-Least-Once）** 投递语义：
- 通知在被确认投递成功前会持续重试
- 外部系统需要具备幂等处理能力
- 选择原因：在"丢失通知"和"重复通知"之间，业务场景更倾向于接受重复

### 重试策略

使用指数退避算法，防止对外部系统造成压力：

```
delay = min(initialDelay * 2^retryCount, maxDelay) ± 20% jitter
```

- 默认初始延迟：1 秒
- 默认最大延迟：1 小时
- 默认最大重试次数：5 次
- 重试序列示例：1s → 2s → 4s → 8s → 16s（加随机抖动）

### 错误处理策略

| 响应类型 | 处理策略 |
|---------|---------|
| 2xx | 标记为 DELIVERED |
| 4xx (非 429) | 标记为 FAILED，不重试 |
| 429 | 重试，使用指数退避 |
| 5xx | 重试，使用指数退避 |
| 连接超时/拒绝 | 重试，使用指数退避 |

### Vendor Adapter 模式

支持不同外部系统的差异化需求：

```java
public interface VendorAdapter {
    String getVendorName();
    DeliveryResult deliver(Notification notification);
    boolean isRetryable(int statusCode, String responseBody);
}
```

- `GenericHttpAdapter`：默认适配器，直接透传请求
- 可扩展特定供应商适配器，处理特殊认证、请求格式等

## API 接口

### 发送通知

```http
POST /api/v1/notifications
Content-Type: application/json

{
  "vendorName": "ad-system",
  "targetUrl": "https://api.vendor.com/webhook",
  "httpMethod": "POST",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer xxx"
  },
  "body": "{\"event\": \"order_created\", \"orderId\": \"12345\"}",
  "idempotencyKey": "order-12345-notification"
}
```

响应：`202 Accepted`
```json
{
  "id": "notification-uuid",
  "status": "PENDING",
  "createdAt": "2026-01-07T12:00:00Z"
}
```

### 查询通知状态

```http
GET /api/v1/notifications/{id}
```

### 查询失败通知列表

```http
GET /api/v1/notifications/failed
GET /api/v1/notifications/failed?vendorName=ad-system
```

### 手动重试

```http
POST /api/v1/notifications/{id}/retry
```

### 取消通知

```http
DELETE /api/v1/notifications/{id}
```

## 关键工程决策与取舍

### 1. 死信队列设计

**AI 建议**：当消息经过最大重试次数之后仍然失败，将 notification 标记为 FAILED，并同时发送到 RabbitMQ DLQ。

**最终决策**：只在 MongoDB 中标记为 FAILED，不发送到 RabbitMQ DLQ。

**原因**：
- MongoDB 是唯一的 source of truth，避免数据不一致
- 通过 `GET /api/v1/notifications/failed` 接口查询失败通知更方便
- 简化架构，减少维护成本

### 2. 可观测性设计

**AI 建议**：只包含粗粒度的 metric。

**最终决策**：按 `vendor_name` 维度观测通知投递状态。

**原因**：
- 及时发现特定供应商的问题
- 便于定位和排查故障
- 支持按供应商设置差异化告警阈值

### 3. 优先级队列

**AI 建议**：引入优先级队列，按通知优先级分流。

**最终决策**：V1 版本不实现，所有消息同等优先级。

**原因**：
- YAGNI 原则，在没有实际需求前不过早设计
- 过早引入会增加系统和业务复杂度
- 当前架构预留了演进空间

### 4. 限流设计

**AI 建议**：实现限流器保护外部系统接口。

**最终决策**：V1 版本不实现供应商级别限流。

**原因**：
- 外部系统应根据自身能力设置限流，而非由内部系统代劳
- 不合理的限流阈值可能影响通知服务可靠性
- 除非有预算限制或额外协议，否则不必要

### 5. 消息队列 API

**AI 建议**：除 HTTP API 外，提供基于消息队列的 API。

**最终决策**：V1 版本只提供 HTTP API。

**原因**：
- 题目要求提供统一、标准化的 HTTP API
- HTTP API 可支撑一般流量压力
- 未来流量增长时可考虑引入

## 系统演进规划

### V1 版本边界

- 单实例部署，RabbitMQ 单节点
- 同步持久化后再入队
- 简单的指数退避重试策略
- 基础的 Prometheus 指标

### 演进方向

| 阶段 | 日通知量 | 演进方案 |
|------|----------|---------|
| 阶段一 | 10万→100万 | 多 Worker 实例、MongoDB 分片、RabbitMQ 集群 |
| 阶段二 | 100万→1000万 | 迁移 Kafka、Redis 缓存、指标聚合 |
| 阶段三 | 复杂度增长 | 优先级队列、供应商限流、通知编排 |

## 运行与测试

### 本地运行

```bash
# 启动依赖服务
docker-compose up -d

# 运行服务
./gradlew :notification-service:bootRun
```

### 运行测试

```bash
./gradlew :notification-service:test
```

### Docker 镜像构建

使用 Spring Boot Gradle 插件的 `bootBuildImage` 任务构建 OCI 镜像：

```bash
# 设置环境变量并构建镜像
export IMAGE_TAG=latest

./gradlew :notification-service:bootBuildImage
```

构建完成后，镜像名称为：`notification-service:${IMAGE_TAG}`

### Docker 方式运行

```bash
# 1. 先启动依赖服务
docker-compose up -d mongodb rabbitmq

# 2. 运行 notification-service 容器
docker run -d \
  --name notification-service \
  --network host \
  -p 8080:8080 \
  notification-service:${IMAGE_TAG}
```
