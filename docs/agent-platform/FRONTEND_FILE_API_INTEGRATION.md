# 通用文件接口前端对接文档

## 1. 接口概览

| 功能 | 方法 | 地址 | 响应类型 | 所需权限 |
| --- | --- | --- | --- | --- |
| 上传文件 | `POST` | `/api/file/upload` | JSON | `/file` 写权限 |
| 预览文件 | `GET` | `/api/file/preview` | 文件二进制 | `/file` 读权限 |
| 下载文件 | `GET` | `/api/file/download` | 文件二进制 | `/file` 读权限 |

所有接口均需要登录，并携带：

```http
Authorization: Bearer <token>
```

服务端默认允许上传不超过 50 MB 的文件。当前接口不限制文件扩展名，业务页面如有类型要求，应在前端选择文件时额外校验。

## 2. 上传文件

### 2.1 请求

```http
POST /api/file/upload
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

表单参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | File | 是 | 待上传文件，不能为空 |

不要手动设置 multipart 请求的 `Content-Type` 边界，由浏览器或请求库自动生成。

### 2.2 成功响应

```json
{
  "code": 200,
  "message": "请求成功",
  "data": {
    "objectKey": "2026/07/18/a30cfc65df314389bd88ec5a5709db32.pdf",
    "fileName": "产品说明书.pdf",
    "contentType": "application/pdf",
    "size": 245760,
    "previewUrl": "http://localhost:8080/api/file/preview?objectKey=2026%2F07%2F18%2Fa30cfc65df314389bd88ec5a5709db32.pdf&fileName=产品说明书.pdf",
    "downloadUrl": "http://localhost:8080/api/file/download?objectKey=2026%2F07%2F18%2Fa30cfc65df314389bd88ec5a5709db32.pdf&fileName=产品说明书.pdf"
  },
  "total": 0
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `objectKey` | string | 文件唯一标识，业务数据需要长期使用文件时应保存该字段 |
| `fileName` | string | 上传时的原始文件名 |
| `contentType` | string | 上传文件的 MIME 类型 |
| `size` | number | 文件大小，单位为字节 |
| `previewUrl` | string | 文件预览接口地址 |
| `downloadUrl` | string | 文件下载接口地址 |

建议业务表至少保存 `objectKey` 和 `fileName`，不要只保存返回的完整 URL。完整 URL 会受到网关域名和部署环境变化影响。

### 2.3 Axios 示例

```ts
export interface FileUploadResult {
  objectKey: string
  fileName: string
  contentType: string
  size: number
  previewUrl: string
  downloadUrl: string
}

export interface WebResponse<T> {
  code: number
  message: string
  data: T
  total: number
}

export async function uploadFile(file: File): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)

  const { data } = await request.post<WebResponse<FileUploadResult>>(
    '/api/file/upload',
    formData,
  )

  if (data.code !== 200) {
    throw new Error(data.message || '文件上传失败')
  }
  return data.data
}
```

## 3. 预览文件

### 3.1 请求参数

```http
GET /api/file/preview?objectKey={objectKey}&fileName={fileName}&contentType={contentType}
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `objectKey` | string | 是 | 上传接口返回的文件标识 |
| `fileName` | string | 否 | 用于 MIME 类型推断和响应文件名，建议传入 |
| `contentType` | string | 否 | 指定响应 MIME 类型；不传时按文件名推断 |

成功时直接返回文件二进制，不使用 `WebResponse` 包装。响应包含：

```http
Content-Disposition: inline; filename*=UTF-8''...
Content-Type: application/pdf
Cache-Control: no-cache
```

### 3.2 前端预览示例

预览和下载接口需要 `Authorization` 请求头，因此不能直接把 `previewUrl` 赋给 `<img src>` 或通过 `window.open(previewUrl)` 打开。应先通过请求库获取 Blob，再生成当前页面有效的 Blob URL。

```ts
export async function createPreviewUrl(file: FileUploadResult): Promise<string> {
  const { data } = await request.get('/api/file/preview', {
    params: {
      objectKey: file.objectKey,
      fileName: file.fileName,
      contentType: file.contentType,
    },
    responseType: 'blob',
  })

  return URL.createObjectURL(data)
}
```

图片预览：

```ts
const previewUrl = await createPreviewUrl(file)
imageElement.src = previewUrl

// 组件卸载或切换文件时释放资源
URL.revokeObjectURL(previewUrl)
```

PDF 可将 Blob URL 赋给 `iframe`/`embed`，或使用 PDF.js。浏览器不原生支持的格式应显示“暂不支持在线预览”，同时保留下载入口。

## 4. 下载文件

### 4.1 请求

```http
GET /api/file/download?objectKey={objectKey}&fileName={fileName}&contentType={contentType}
```

参数含义与预览接口一致。成功响应使用：

```http
Content-Disposition: attachment; filename*=UTF-8''...
```

### 4.2 Axios 示例

```ts
export async function downloadFile(file: FileUploadResult): Promise<void> {
  const { data } = await request.get('/api/file/download', {
    params: {
      objectKey: file.objectKey,
      fileName: file.fileName,
      contentType: file.contentType,
    },
    responseType: 'blob',
  })

  const url = URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName || 'file'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
```

## 5. 错误处理

上传接口失败时返回 JSON：

```json
{
  "code": 413,
  "message": "文件大小超过限制",
  "data": null,
  "total": 0
}
```

常见业务错误：

| code | 场景 | 前端处理建议 |
| --- | --- | --- |
| `400` | `objectKey` 非法 | 提示文件标识无效，不再重试 |
| `401` | 未登录或 token 失效 | 进入统一重新登录流程 |
| `403` | 没有 `/file` 读写权限 | 显示无权限提示 |
| `413` | 文件超过服务端限制 | 提示用户压缩或选择较小文件 |
| `422` | 文件为空 | 提示重新选择文件 |
| `500` | MinIO 未配置、不可用或文件不存在 | 显示加载失败，并允许用户重试 |

预览和下载请求使用 `responseType: 'blob'` 时，错误 JSON 也可能被请求库包装为 Blob。统一拦截器需要根据响应 `Content-Type` 是否为 `application/json`，将 Blob 转成文本后再解析错误信息。

```ts
async function parseBlobError(error: any): Promise<never> {
  const body = error?.response?.data
  if (body instanceof Blob && body.type.includes('application/json')) {
    const result = JSON.parse(await body.text())
    throw new Error(result.message || '文件请求失败')
  }
  throw error
}
```

## 6. 权限与业务接入要求

- 菜单/权限资源中需要为用户授予 `/file` 权限；上传要求写权限，预览和下载要求读权限。
- `objectKey` 是后续访问文件的关键字段，禁止前端自行拼接或修改。
- 前端展示文件名时使用 `fileName`，存储定位使用 `objectKey`，两者不可混用。
- 上传成功但业务表单最终未提交时，目前不会自动清理已上传文件；前端无需主动删除，但产品侧应关注孤立文件治理。
- 接口当前一次只上传一个文件；多文件场景由前端逐个调用，建议限制并发数并分别记录失败项。

## 7. 后端环境配置

```yaml
storage:
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
  file:
    bucket: ${MINIO_FILE_BUCKET:aether-file}
    max-size: ${FILE_MAX_SIZE:52428800}
```

`storage.file.max-size` 的单位是字节。它应不大于 Spring Multipart 的 `spring.servlet.multipart.max-file-size`，否则请求会在进入文件控制器前被 Spring 拒绝。
