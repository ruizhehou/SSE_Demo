# SSE Demo

基于 Spring Boot 实现的 Server-Sent Events（SSE）服务端示例，演示如何通过 HTTP 长连接从服务端向客户端实时推送事件。

## 技术栈

- Java 17
- Spring Boot 3.3.0
- Spring Web（SseEmitter）

## 启动

```bash
./mvnw spring-boot:run
```

服务启动在 `http://localhost:8080`。

## 接口说明

### 订阅 SSE 流

```
GET /sse/subscribe/{clientId}
```

建立 SSE 长连接，连接成功后服务端立即推送一条 `connect` 事件。

```bash
curl -N http://localhost:8080/sse/subscribe/client1
```

---

### 向指定客户端推送消息

```
GET /sse/push/{clientId}?message={内容}
```

向指定客户端发送一条 `message` 事件。

```bash
curl "http://localhost:8080/sse/push/client1?message=你好"
```

---

### 广播给所有客户端

```
GET /sse/broadcast?message={内容}
```

向所有在线客户端发送一条 `broadcast` 事件。

```bash
curl "http://localhost:8080/sse/broadcast?message=大家好"
```

---

### 启动定时推送演示

```
GET /sse/demo/{clientId}
```

启动后每隔 1 秒向指定客户端推送一条服务端时间戳（`tick` 事件）。

```bash
curl "http://localhost:8080/sse/demo/client1"
```

## 实现原理

服务端用 `ConcurrentHashMap<String, SseEmitter>` 维护所有在线连接，以 `clientId` 作为 key。客户端订阅时创建 `SseEmitter` 并存入 Map，断开连接时自动移除。

```
客户端                              服务端
  |                                   |
  |-- GET /sse/subscribe/client1 ---> |  创建 SseEmitter，存入 Map
  |<-- text/event-stream ------------ |
  |<-- event: connect --------------- |
  |                                   |
  |      （另一个请求触发推送）           |
  |                                   |<-- GET /sse/push/client1?message=你好
  |<-- event: message, data: 你好 ---- |
  |                                   |
  |（客户端断开）                        |
  |                                   |  onCompletion → 从 Map 移除
```
