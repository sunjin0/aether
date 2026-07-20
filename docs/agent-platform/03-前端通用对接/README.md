# Agent 平台 — 前端通用对接：文件接口

> 原文档：FRONTEND_FILE_API_INTEGRATION.md

---

## 通用文件接口

### 接口概览

| 功能 | 方法 | 地址 | 权限 |
|------|------|------|------|
| 上传文件 | POST | `/api/file/upload` | `/file` 写权限 |
| 预览文件 | GET | `/api/file/preview` | `/file` 读权限 |
| 下载文件 | GET | `/api/file/download` | `/file` 读权限 |

### 文件上传

- **Content-Type**: `multipart/form-data`
- **参数**: `file`（必填）
- **响应**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "objectKey": "uuid-filename.pdf",
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "size": 1024000,
    "previewUrl": "/api/file/preview?objectKey=...",
    "downloadUrl": "/api/file/download?objectKey=..."
  }
}
```

### 文件预览

- GET 请求，返回文件二进制流
- 需要 `Authorization` 请求头
- **不能直接使用** `<img src>` 或 `window.open()` — 需先获取 Blob 再生成 URL

```javascript
// 前端预览示例
const response = await fetch(`/api/file/preview?objectKey=${key}`, {
  headers: { Authorization: `Bearer ${token}` }
});
const blob = await response.blob();
const url = URL.createObjectURL(blob);
```

### 文件下载

- GET 请求，返回 `Content-Disposition: attachment`
- 同样需要 `Authorization` 请求头

### 错误处理

预览/下载使用 `responseType: 'blob'` 时，错误 JSON 也可能被包装为 Blob。统一拦截器需按 `Content-Type` 区分：
- `application/json` → 解析 JSON 错误信息
- `application/octet-stream` → 正常文件内容
