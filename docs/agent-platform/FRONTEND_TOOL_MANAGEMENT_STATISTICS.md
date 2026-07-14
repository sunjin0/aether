# 工具管理模块前端对接说明

> 日期：2026-07-14
> 范围：工具管理列表、工具统计卡片、工具业务类型
> 目标读者：前端开发

---

## 1. 本次变更概览

工具管理模块新增以下后端能力：

1. `agent_tool` 新增工具业务类型字段 `toolType`，用于按使用场景分类工具，例如信息库、运维、开发等。
2. 工具列表接口 `/api/agent/tool/list` 每条工具记录新增调用统计字段：
   - `callCount`：该工具累计调用次数
   - `successRate`：该工具调用成功率，单位为百分比，取值如 `95.50`
3. 工具管理模块新增统计卡片接口 `/api/agent/tool/statistics`，用于页面顶部统计卡片。

---

## 2. 工具业务类型字段

### 2.1 字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `toolType` | String | 工具业务类型，用于分类工具使用场景 |

建议前端展示为下拉筛选或标签。

示例值可按产品配置：

| 展示文案 | 建议值 |
| --- | --- |
| 信息库 | `knowledge` |
| 运维 | `ops` |
| 开发 | `dev` |
| 通用 | `general` |

后端当前按字符串精确匹配，不限制枚举值。前端如果已有字典体系，建议接入字典；如果暂无字典，可以先用本地选项。

### 2.2 新增/编辑工具

接口不变：

```http
POST /api/agent/tool
PUT /api/agent/tool/{id}
```

请求体新增 `toolType`：

```json
{
  "name": "查询知识库",
  "code": "search_knowledge",
  "description": "查询内部知识库",
  "toolType": "knowledge",
  "mcpServerId": "1001",
  "mcpToolName": "search_knowledge",
  "mcpInputSchema": "{}",
  "timeoutMs": 30000,
  "status": 1,
  "remark": ""
}
```

---

## 3. 工具列表接口

### 3.1 接口

```http
POST /api/agent/tool/list
Authorization: Bearer {token}
```

### 3.2 请求参数

```json
{
  "name": "",
  "code": "",
  "toolType": "knowledge",
  "mcpServerId": "",
  "status": 1,
  "current": 1,
  "pageSize": 10
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String | 否 | 工具名称，模糊查询 |
| `code` | String | 否 | 工具编码，模糊查询 |
| `toolType` | String | 否 | 工具业务类型，精确查询 |
| `mcpServerId` | String | 否 | MCP 服务 ID |
| `status` | Number | 否 | 状态：`0` 禁用，`1` 启用 |
| `current` | Number | 是 | 当前页 |
| `pageSize` | Number | 是 | 每页条数 |

### 3.3 响应字段变更

列表每条新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `toolType` | String | 工具业务类型 |
| `callCount` | Number | 工具累计调用次数；无调用记录时为 `0` |
| `successRate` | Number | 工具调用成功率百分比；无调用记录时为 `0` |

示例：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "1001",
      "name": "查询知识库",
      "code": "search_knowledge",
      "description": "查询内部知识库",
      "toolType": "knowledge",
      "mcpServerId": "2001",
      "mcpServerName": "Knowledge MCP",
      "mcpBaseUrl": "https://mcp.example.com",
      "mcpToolName": "search_knowledge",
      "timeoutMs": 30000,
      "status": 1,
      "callCount": 128,
      "successRate": 96.88,
      "remark": "",
      "createdAt": 1783769933000,
      "updatedAt": 1783769933000
    }
  ],
  "total": 1
}
```

### 3.4 前端展示建议

工具列表建议新增列：

| 列 | 字段 | 展示建议 |
| --- | --- | --- |
| 业务类型 | `toolType` | 映射为中文标签 |
| 调用次数 | `callCount` | 数字展示 |
| 成功率 | `successRate` | 加 `%` 展示，保留 2 位小数 |

成功率展示示例：

```ts
const formatRate = (value?: number) => `${(value ?? 0).toFixed(2)}%`
```

---

## 4. 工具统计卡片接口

### 4.1 接口

```http
GET /api/agent/tool/statistics?toolType=knowledge&mcpServerId=2001
Authorization: Bearer {token}
```

### 4.2 Query 参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `toolType` | String | 否 | 按工具业务类型筛选 |
| `mcpServerId` | String | 否 | 按 MCP 服务筛选 |

如果页面筛选区选择了业务类型或 MCP 服务，建议同步传给统计接口，使卡片统计范围与列表筛选范围一致。

### 4.3 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalCount": 24,
    "enabledCount": 20,
    "disabledCount": 4,
    "callCount": 3560,
    "successCount": 3422,
    "successRate": 96.12359550561797
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | Number | 工具总数 |
| `enabledCount` | Number | 启用工具数 |
| `disabledCount` | Number | 禁用工具数 |
| `callCount` | Number | 调用总次数 |
| `successCount` | Number | 成功调用次数 |
| `successRate` | Number | 总体成功率百分比 |

### 4.4 卡片建议

建议工具管理页顶部展示 4 个卡片：

| 卡片 | 字段 | 展示 |
| --- | --- | --- |
| 工具总数 | `totalCount` | `24` |
| 启用工具 | `enabledCount` | `20` |
| 调用次数 | `callCount` | `3,560` |
| 成功率 | `successRate` | `96.12%` |

可选补充展示：

| 卡片/辅助文本 | 字段 | 展示 |
| --- | --- | --- |
| 禁用工具 | `disabledCount` | `4` |
| 成功调用 | `successCount` | `3,422` |

---

## 5. 页面联动建议

前端进入工具管理页时：

1. 调用 `/api/agent/tool/statistics` 加载统计卡片。
2. 调用 `/api/agent/tool/list` 加载工具表格。
3. 用户修改筛选条件后，同时刷新统计卡片和列表。

示例流程：

```ts
async function reloadToolPage(filters) {
  await Promise.all([
    fetchToolStatistics({
      toolType: filters.toolType,
      mcpServerId: filters.mcpServerId
    }),
    fetchToolList({
      ...filters,
      current: pagination.current,
      pageSize: pagination.pageSize
    })
  ])
}
```

---

## 6. 兼容性说明

1. 旧前端如果不传 `toolType`，列表仍按原逻辑返回全部工具。
2. 新增 `toolType` 字段需要数据库执行迁移脚本：

```sql
api/src/main/resources/sql/agent-tool-type-migration.sql
```

3. `successRate` 是百分比数值，不是小数比例；前端不要再乘以 100。
4. 无调用记录时，列表项 `callCount=0`、`successRate=0`。
