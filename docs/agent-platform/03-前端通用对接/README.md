# Agent 平台 — 前端通用对接：文件接口

> 原文档：FRONTEND_FILE_API_INTEGRATION.md

---

## 通用文件接口

### 接口概览

| 功能 | 方法 | 地址 | 权限 |
|------|------|------|------|
| 上传文件 | POST | `/api/file/upload` | 公开，无资源权限 |
| 预览文件 | GET | `/api/file/preview` | 公开，无资源权限 |
| 下载文件 | GET | `/api/file/download` | 公开，无资源权限 |

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
- 当前 `FileController` 不强制 `Authorization` 或 `/file` 资源权限；如部署层另有限制，以部署配置为准。
- 可直接使用 URL，或按前端统一错误处理策略先获取 Blob 再生成 URL。

```javascript
// 前端预览示例
const response = await fetch(`/api/file/preview?objectKey=${key}`, {
  headers: { Authorization: `Bearer ${token}` } // 可选：同源登录态或网关要求时携带
});
const blob = await response.blob();
const url = URL.createObjectURL(blob);
```

### 文件下载

- GET 请求，返回 `Content-Disposition: attachment`
- 当前实现不强制 `Authorization` 请求头

### 错误处理

预览/下载使用 `responseType: 'blob'` 时，错误 JSON 也可能被包装为 Blob。统一拦截器需按 `Content-Type` 区分：
- `application/json` → 解析 JSON 错误信息
- `application/octet-stream` → 正常文件内容
