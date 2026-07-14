# MCP 工具模块前端对接方案

## 1. 目标

工具模块改为两层 MCP 模式：

```text
agent_mcp_server 1 ---- N agent_tool
```

前端不再让用户为每个工具重复配置 endpoint、header、auth。用户先配置一个 MCP 服务，再从该服务发现并批量导入工具，Agent 绑定的是具体 `agent_tool`。

## 2. 模块结构

MCP 服务需要作为独立菜单模块，不使用工具模块内的 Tab。

建议菜单：

- MCP 服务管理：管理 MCP 服务连接配置。
- MCP 工具管理：管理从 MCP 服务导入的具体工具。

Agent 工具绑定页面继续绑定具体工具，不直接绑定 MCP 服务。

## 3. 字典接口

前端类型下拉不要硬编码，统一通过系统字典接口获取：

```http
GET /api/sys/dict/options?parentCode={parentCode}&useValue=true
```

返回：

```json
[
  {
    "label": "启用",
    "value": "1"
  }
]
```

字段和字典关系：

| 使用位置 | 字段 | parentCode | value |
| --- | --- | --- | --- |
| MCP 服务传输类型 | `transport` | `Agent_Mcp_Transport` | `http` / `streamable_http` |
| MCP 服务认证类型 | `authType` | `Agent_Mcp_Auth_Type` | `none` / `bearer` / `api_key` |
| MCP 服务状态 | `status` | `Agent_Status` | `0` / `1` |
| MCP 工具状态 | `status` | `Agent_Status` | `0` / `1` |

前端拿到字典 `value` 后，如果目标字段是数字状态，需要转成数字再提交：

```js
status: Number(option.value)
```

## 4. MCP 服务管理

### 4.1 列表

```http
POST /api/agent/mcp-server/list
```

请求：

```json
{
  "name": "",
  "code": "",
  "transport": "http",
  "status": 1,
  "current": 1,
  "pageSize": 10
}
```

展示字段：

- `name`：服务名称
- `code`：服务编码
- `transport`：传输类型
- `baseUrl`：MCP endpoint
- `authType`：认证类型
- `timeoutMs`：超时时间
- `status`：状态
- `updatedAt`：更新时间

注意：`authToken` 后端不回显，列表和详情里都按空处理。

### 4.2 新增服务

```http
POST /api/agent/mcp-server
```

请求：

```json
{
  "name": "搜索 MCP 服务",
  "code": "search_mcp",
  "transport": "http",
  "baseUrl": "http://localhost:3000/mcp",
  "requestHeaders": "{\"X-App\":\"aether\"}",
  "authType": "none",
  "authToken": "",
  "timeoutMs": 30000,
  "status": 1,
  "remark": ""
}
```

字段说明：

- `transport`：下拉选项来自字典 `Agent_Mcp_Transport`。
- `baseUrl`：必填，MCP 服务地址。
- `requestHeaders`：JSON 字符串，前端保存前校验 JSON 合法性。
- `authType`：下拉选项来自字典 `Agent_Mcp_Auth_Type`。
- `authToken`：`authType !== none` 时展示；编辑时留空表示不修改 token。
- `timeoutMs`：默认 `30000`。
- `status`：下拉/开关选项来自字典 `Agent_Status`，提交时转为数字。

### 4.3 编辑服务

```http
PUT /api/agent/mcp-server/{id}
```

请求体同新增。

编辑注意事项：

- 详情接口不回显 `authToken`。
- token 输入框默认空。
- 用户填写 token 时提交新 token。
- 用户不填写 token 时表示不修改旧 token。
- 用户需要清空 token 时，提交 `clearAuthToken: true`。

### 4.4 详情

```http
GET /api/agent/mcp-server/{id}
```

返回字段同列表，`authToken` 为 `null`。

### 4.5 删除

```http
DELETE /api/agent/mcp-server/{id}
```

如果该服务下仍有关联工具，后端会拒绝删除。前端提示：

```text
该 MCP 服务下仍有关联工具，请先删除或迁移工具。
```

## 5. MCP 工具发现与导入

### 5.1 发现服务 tools

```http
POST /api/agent/mcp-server/{id}/tools
```

返回：

```json
[
  {
    "name": "search",
    "description": "Search documents",
    "inputSchema": "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"
  }
]
```

前端交互：

- 在 MCP 服务列表行提供“发现工具”按钮。
- 打开弹窗展示该服务暴露的 tools。
- 支持多选。
- 展示 `name`、`description`。
- `inputSchema` 可折叠展示，推荐用 JSON viewer。

### 5.2 批量导入工具

```http
POST /api/agent/mcp-server/{id}/import-tools
```

导入全部：

```json
{}
```

导入选中工具：

```json
{
  "toolNames": ["search", "fetch"]
}
```

后端行为：

- 自动跳过同一服务下已存在的工具。
- 自动生成工具 `code`。
- 保存 `mcpServerId`、`mcpToolName`、`mcpInputSchema`。
- 默认 `status = 1`。

导入成功后：

- 关闭弹窗或展示导入结果。
- 刷新 MCP 工具列表。

## 6. MCP 工具管理

### 6.1 列表

```http
POST /api/agent/tool/list
```

请求：

```json
{
  "name": "",
  "code": "",
  "mcpServerId": "",
  "status": 1,
  "current": 1,
  "pageSize": 10
}
```

展示字段：

- `name`：工具名称
- `code`：工具编码
- `mcpServerName`：所属 MCP 服务
- `mcpBaseUrl`：MCP 服务地址，仅展示
- `mcpToolName`：MCP 原始工具名
- `status`：工具启停
- `updatedAt`：更新时间

筛选项：

- 工具名称
- 工具编码
- MCP 服务
- 状态

不再展示：

- 工具类型
- HTTP 方法
- HTTP URL
- HTTP Headers
- HTTP Body Template
- 响应提取规则
- 工具级 MCP endpoint/header/auth

### 6.2 新增工具

```http
POST /api/agent/tool
```

请求：

```json
{
  "name": "search",
  "code": "search_mcp_search",
  "description": "Search documents",
  "mcpServerId": "1900000000000000001",
  "mcpToolName": "search",
  "mcpInputSchema": "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
  "timeoutMs": 30000,
  "status": 1,
  "remark": ""
}
```

通常不建议用户手动新增，优先通过“发现工具 -> 批量导入”生成。

### 6.3 编辑工具

```http
PUT /api/agent/tool/{id}
```

可编辑字段：

- `name`
- `code`
- `description`
- `mcpServerId`
- `mcpToolName`
- `mcpInputSchema`
- `timeoutMs`
- `status`
- `remark`

建议 UI：

- `mcpServerId` 使用 MCP 服务下拉选择。
- `mcpToolName` 可手动编辑，但推荐从服务发现结果选择。
- `mcpInputSchema` 使用 JSON editor。

### 6.4 详情

```http
GET /api/agent/tool/{id}
```

用于编辑表单回显。

### 6.5 删除

```http
DELETE /api/agent/tool/{id}
```

删除具体工具，不影响 MCP 服务配置。

### 6.6 测试工具

```http
POST /api/agent/tool/{id}/test
```

请求体为 MCP tool arguments：

```json
{
  "query": "测试关键词"
}
```

前端建议：

- 优先根据 `mcpInputSchema` 动态生成参数表单。
- 第一版可用 JSON editor 输入 arguments。
- 展示结果字段：
  - `success`
  - `content`
  - `rawResponse`
  - `latencyMs`
  - `errorMsg`
  - `requestUrl`
  - `requestMethod`

## 7. Agent 工具绑定页面

绑定对象仍然是 `agent_tool`。

需要调整：

- 工具选择器展示 `name`、`code`、`mcpServerName`、`mcpToolName`。
- 只展示或优先展示 `status = 1` 的工具。
- 不展示 MCP 服务 auth/header 等连接细节。

## 8. 推荐交互流程

### 7.1 新接入一个 MCP 服务

1. 进入 MCP 服务页。
2. 点击“新增服务”。
3. 填写 endpoint、transport、auth、headers。
4. 保存服务。
5. 点击“发现工具”。
6. 勾选要导入的 tools。
7. 点击“导入工具”。
8. 进入 MCP 工具页检查工具信息。
9. 在 Agent 配置中绑定这些工具。

### 7.2 MCP 服务新增 tool 后同步

1. 在 MCP 服务列表点击“发现工具”。
2. 前端展示当前服务返回的 tools。
3. 标记已导入和未导入。
4. 用户勾选未导入工具。
5. 调用批量导入接口。

### 7.3 更新 MCP 服务认证

1. 编辑 MCP 服务。
2. 修改 `authType` / `authToken` / `requestHeaders`。
3. 保存。
4. 所有关联工具自动使用新服务配置，无需逐个修改工具。

## 9. 前端状态与校验

必填校验：

- MCP 服务：`name`、`code`、`transport`、`baseUrl`
- MCP 工具：`name`、`code`、`mcpServerId`、`mcpToolName`

格式校验：

- `requestHeaders` 必须是合法 JSON 字符串或空。
- `mcpInputSchema` 必须是合法 JSON 字符串或空。
- `timeoutMs` 必须大于 0。

状态：

- 状态选项来自字典 `Agent_Status`。
- 提交 MCP 服务或 MCP 工具时，`status` 需要是数字。

禁用 MCP 服务后：

- 该服务下工具仍存在。
- 执行工具时后端会返回 MCP 服务未启用。
- 前端可在工具列表中对服务禁用状态做提示。

## 10. 当前不支持项

当前前端不要提供：

- `stdio` transport
- 工具级 endpoint/header/auth 配置
- HTTP 工具模式
- response extract rule
- HTTP method/body template

`stdio` 后续如果支持，需要后端新增进程管理、命令白名单、环境变量配置和 stdio transport。
