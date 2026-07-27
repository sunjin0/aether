param(
    [string]$OutputDir = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Write-Utf8File {
    param([string]$Path, [string]$Content)
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Escape-Xml {
    param([string]$Text)
    return [System.Security.SecurityElement]::Escape($Text)
}

function Escape-PdfText {
    param([string]$Text)
    return $Text.Replace('\', '\\').Replace('(', '\(').Replace(')', '\)')
}

function Add-ZipEntry {
    param(
        [System.IO.Compression.ZipArchive]$Zip,
        [string]$Name,
        [string]$Content
    )
    $entry = $Zip.CreateEntry($Name)
    $stream = $entry.Open()
    $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
    $writer.Write($Content)
    $writer.Dispose()
    $stream.Dispose()
}

function New-Docx {
    param([string]$Path, [string[]]$Paragraphs)
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path }
    $zip = [System.IO.Compression.ZipFile]::Open($Path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipEntry $zip '[Content_Types].xml' @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>
'@
        Add-ZipEntry $zip '_rels/.rels' @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>
'@
        $body = New-Object System.Text.StringBuilder
        foreach ($p in $Paragraphs) {
            $style = if ($p.StartsWith('# ')) { 'Title' } elseif ($p.StartsWith('## ')) { 'Heading1' } else { 'Normal' }
            $text = $p -replace '^#+\s*', ''
            [void]$body.Append("<w:p><w:pPr><w:pStyle w:val=`"$style`"/></w:pPr><w:r><w:t xml:space=`"preserve`">$(Escape-Xml $text)</w:t></w:r></w:p>")
        }
        Add-ZipEntry $zip 'word/document.xml' "<?xml version=`"1.0`" encoding=`"UTF-8`" standalone=`"yes`"?><w:document xmlns:w=`"http://schemas.openxmlformats.org/wordprocessingml/2006/main`"><w:body>$body<w:sectPr><w:pgSz w:w=`"11906`" w:h=`"16838`"/><w:pgMar w:top=`"1440`" w:right=`"1440`" w:bottom=`"1440`" w:left=`"1440`"/></w:sectPr></w:body></w:document>"
    } finally {
        $zip.Dispose()
    }
}

function ColumnName {
    param([int]$Index)
    $name = ''
    while ($Index -gt 0) {
        $mod = ($Index - 1) % 26
        $name = [char](65 + $mod) + $name
        $Index = [math]::Floor(($Index - $mod) / 26)
    }
    return $name
}

function New-Xlsx {
    param([string]$Path, [hashtable]$Sheets)
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path }
    $zip = [System.IO.Compression.ZipFile]::Open($Path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $sheetNames = @($Sheets.Keys)
        $overrides = ''
        $rels = ''
        $sheetsXml = ''
        for ($i = 0; $i -lt $sheetNames.Count; $i++) {
            $id = $i + 1
            $overrides += "<Override PartName=`"/xl/worksheets/sheet$id.xml`" ContentType=`"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`"/>"
            $rels += "<Relationship Id=`"rId$id`" Type=`"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet`" Target=`"worksheets/sheet$id.xml`"/>"
            $sheetsXml += "<sheet name=`"$(Escape-Xml $sheetNames[$i])`" sheetId=`"$id`" r:id=`"rId$id`"/>"
        }
        Add-ZipEntry $zip '[Content_Types].xml' "<?xml version=`"1.0`" encoding=`"UTF-8`" standalone=`"yes`"?><Types xmlns=`"http://schemas.openxmlformats.org/package/2006/content-types`"><Default Extension=`"rels`" ContentType=`"application/vnd.openxmlformats-package.relationships+xml`"/><Default Extension=`"xml`" ContentType=`"application/xml`"/><Override PartName=`"/xl/workbook.xml`" ContentType=`"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`"/>$overrides</Types>"
        Add-ZipEntry $zip '_rels/.rels' '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'
        Add-ZipEntry $zip 'xl/_rels/workbook.xml.rels' "<?xml version=`"1.0`" encoding=`"UTF-8`" standalone=`"yes`"?><Relationships xmlns=`"http://schemas.openxmlformats.org/package/2006/relationships`">$rels</Relationships>"
        Add-ZipEntry $zip 'xl/workbook.xml' "<?xml version=`"1.0`" encoding=`"UTF-8`" standalone=`"yes`"?><workbook xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`" xmlns:r=`"http://schemas.openxmlformats.org/officeDocument/2006/relationships`"><sheets>$sheetsXml</sheets></workbook>"
        for ($i = 0; $i -lt $sheetNames.Count; $i++) {
            $rows = $Sheets[$sheetNames[$i]]
            $sheetData = ''
            for ($r = 0; $r -lt $rows.Count; $r++) {
                $rowNum = $r + 1
                $cells = ''
                for ($c = 0; $c -lt $rows[$r].Count; $c++) {
                    $col = ColumnName ($c + 1)
                    $value = Escape-Xml ([string]$rows[$r][$c])
                    $cells += "<c r=`"$col$rowNum`" t=`"inlineStr`"><is><t>$value</t></is></c>"
                }
                $sheetData += "<row r=`"$rowNum`">$cells</row>"
            }
            Add-ZipEntry $zip "xl/worksheets/sheet$($i + 1).xml" "<?xml version=`"1.0`" encoding=`"UTF-8`" standalone=`"yes`"?><worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData>$sheetData</sheetData></worksheet>"
        }
    } finally {
        $zip.Dispose()
    }
}

function New-SimplePdf {
    param([string]$Path, [string[]]$Lines)
    $objects = New-Object System.Collections.Generic.List[string]
    $objects.Add('<< /Type /Catalog /Pages 2 0 R >>')
    $objects.Add('<< /Type /Pages /Kids [3 0 R] /Count 1 >>')
    $objects.Add('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>')
    $objects.Add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')
    $content = "BT /F1 10 Tf 50 800 Td 14 TL`n"
    foreach ($line in $Lines) {
        $safe = Escape-PdfText $line
        $content += "($safe) Tj T*`n"
    }
    $content += 'ET'
    $objects.Add("<< /Length $($content.Length) >>`nstream`n$content`nendstream")
    $bytes = New-Object System.Collections.Generic.List[byte]
    $enc = [System.Text.Encoding]::ASCII
    function AppendAscii([string]$s) { $bytes.AddRange($enc.GetBytes($s)) }
    AppendAscii "%PDF-1.4`n"
    $offsets = @(0)
    for ($i = 0; $i -lt $objects.Count; $i++) {
        $offsets += $bytes.Count
        AppendAscii "$($i + 1) 0 obj`n$($objects[$i])`nendobj`n"
    }
    $xref = $bytes.Count
    AppendAscii "xref`n0 $($objects.Count + 1)`n0000000000 65535 f `n"
    for ($i = 1; $i -lt $offsets.Count; $i++) { AppendAscii (("{0:D10} 00000 n `n" -f $offsets[$i])) }
    AppendAscii "trailer << /Size $($objects.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF"
    [System.IO.File]::WriteAllBytes($Path, $bytes.ToArray())
}

$productMd = @'
# 星澜工单平台产品手册 v3.2

星澜工单平台是一个面向企业服务台、IT 运维和客户成功团队的工单协作系统。本测试资料为虚构内容，用于 RAG 检索、重排、问答和溯源评估。

## 核心模块

| 模块 | 说明 | 默认启用套餐 |
| --- | --- | --- |
| Ticket Hub | 工单创建、分派、流转、合并和关闭 | Standard |
| Flow Desk | SLA 计时、自动升级、自动提醒 | Professional |
| Insight Lens | 报表、趋势分析、知识命中率 | Enterprise |
| Guard Center | 权限、审计、字段脱敏 | Enterprise |

## 关键术语

- 工单优先级 P0 表示业务完全中断，目标首次响应时间为 15 分钟。
- 工单优先级 P1 表示核心功能不可用，目标首次响应时间为 30 分钟。
- “客户可见备注”会展示给外部请求人；“内部处理备注”仅坐席、主管和审计员可见。
- “自动升级”指 SLA 即将超时时提升处理层级，不等同于“版本升级”。

## 套餐限制

Standard 套餐最多支持 50 名坐席、3 个工单队列和 90 天数据留存。Professional 套餐最多支持 300 名坐席、20 个工单队列和 365 天数据留存。Enterprise 套餐不限制坐席数量，默认数据留存为 1095 天，可通过合同延长。

## 重要版本规则

从 v3.2 起，知识库命中率统计口径改为“被用户采纳的答案数 / 已触发知识推荐的工单数”。v3.1 及更早版本使用“推荐答案展示次数 / 工单总数”，该口径已废弃。

## 检索干扰点

星澜平台中的“队列”不是消息队列；它表示工单的业务承接组。Flow Desk 中的“升级”不是软件版本升级；它表示 SLA 处理层级升级。
'@
Write-Utf8File (Join-Path $OutputDir '01-product-handbook.md') $productMd
New-SimplePdf (Join-Path $OutputDir '01-product-handbook.pdf') @(
    'Xinglan Ticket Platform Product Handbook v3.2',
    'Ticket Hub: ticket creation, assignment, merge, close. Standard enabled.',
    'Flow Desk: SLA timer, auto escalation, reminders. Professional enabled.',
    'Insight Lens: reports and knowledge hit rate. Enterprise enabled.',
    'Guard Center: permission, audit, field masking. Enterprise enabled.',
    'P0 first response target: 15 minutes. P1 first response target: 30 minutes.',
    'Customer-visible notes are external; internal handling notes are visible to agents, supervisors, auditors.',
    'Since v3.2, knowledge hit rate = accepted answers / tickets that triggered knowledge recommendation.',
    'In v3.1 and earlier, the old hit-rate formula is deprecated.',
    'Queue means business assignment group, not message queue. Escalation means SLA escalation, not software upgrade.'
)

New-Docx (Join-Path $OutputDir '02-deployment-guide.docx') @(
    '# 星澜工单平台部署手册 v3.2',
    '适用范围：单机测试环境、高可用生产环境和灰度发布环境。本文档中的端口与配置项用于 RAG 测试，不代表真实系统要求。',
    '## 环境要求',
    'JDK 17 或更高版本；PostgreSQL 14；Redis 6.2；对象存储兼容 S3 协议。最低 CPU 为 4 核，最低内存为 8GB。生产环境建议 8 核 32GB。',
    '## 默认端口',
    'Web 控制台端口为 8088。API 服务端口为 8090。任务调度服务端口为 8092。内部健康检查端口为 19090。',
    '## 关键配置项',
    'STARLAN_DB_URL 表示数据库连接地址。STARLAN_REDIS_URL 表示 Redis 连接地址。STARLAN_OBJECT_BUCKET 表示附件对象存储桶。STARLAN_SLA_WORKER_SIZE 默认值为 8，超过 2000 坐席时建议设置为 32。',
    '## 升级步骤',
    '1. 备份数据库和对象存储元数据。2. 暂停 Flow Desk 的自动升级任务。3. 部署新版本 API 服务。4. 执行数据库迁移。5. 恢复自动升级任务。',
    '## 回滚步骤',
    '如果 v3.2 升级失败，允许回滚到 v3.1.7，但必须先禁用新口径的知识命中率任务，否则旧版本报表会出现字段缺失。',
    '## 易混淆信息',
    '部署手册中的升级指软件版本升级；产品手册中的自动升级指 SLA 层级升级。'
)

New-Docx (Join-Path $OutputDir '03-permission-policy.docx') @(
    '# 星澜工单平台权限策略 v2026.07',
    '本策略覆盖坐席、主管、审计员、外部请求人和系统管理员。权限判断采用角色权限、队列归属和字段级规则叠加。',
    '## 角色权限',
    '坐席 Agent 可以查看自己所在队列的工单，可以编辑内部处理备注，但不能删除审计日志。',
    '主管 Supervisor 可以跨队列重新分派工单，可以批准 P0 工单的 SLA 暂停申请。',
    '审计员 Auditor 可以查看审计日志和内部处理备注，但不能修改工单状态。',
    '外部请求人 Requester 只能查看自己提交的工单和客户可见备注。',
    '系统管理员 Admin 可以配置队列、角色、字段脱敏和系统集成，但默认不参与工单处理。',
    '## 字段级规则',
    '手机号、邮箱和合同金额属于敏感字段。Standard 套餐仅支持手机号脱敏；Professional 支持手机号和邮箱脱敏；Enterprise 支持自定义字段脱敏。',
    '## 审批规则',
    'P0 工单暂停 SLA 必须由主管批准。超过 4 小时的 SLA 暂停必须由主管和审计员双人确认。',
    '## 版本差异',
    '自 2026.07 策略起，审计员可以查看内部处理备注。2026.05 策略中审计员不能查看内部处理备注，该规则已失效。'
)

$billingSheets = @{
    '套餐限制' = @(
        @('套餐', '坐席上限', '队列上限', '数据留存天数', '字段脱敏', '知识命中率报表'),
        @('Standard', '50', '3', '90', '仅手机号', '不支持'),
        @('Professional', '300', '20', '365', '手机号和邮箱', '支持基础报表'),
        @('Enterprise', '不限制', '不限制', '1095', '自定义字段', '支持高级报表')
    )
    'SLA目标' = @(
        @('优先级', '首次响应', '解决目标', '自动升级阈值', '适用套餐'),
        @('P0', '15分钟', '4小时', '剩余10分钟', 'Professional及以上'),
        @('P1', '30分钟', '8小时', '剩余20分钟', 'Professional及以上'),
        @('P2', '4小时', '2个工作日', '剩余4小时', 'Standard及以上'),
        @('P3', '1个工作日', '5个工作日', '无默认升级', 'Standard及以上')
    )
    '例外条款' = @(
        @('条款编号', '内容', '是否计入SLA'),
        @('EX-01', '客户未提供必要日志导致等待', '不计入'),
        @('EX-02', '计划内维护窗口', '不计入'),
        @('EX-03', '平台自身故障', '计入'),
        @('EX-04', '第三方云厂商区域级故障', '合同另行约定')
    )
}
New-Xlsx (Join-Path $OutputDir '04-billing-and-sla.xlsx') $billingSheets

$releaseSheets = @{
    '版本记录' = @(
        @('版本', '发布日期', '状态', '关键变化'),
        @('v3.2.0', '2026-07-15', '当前版本', '知识命中率采用采纳口径；审计员可查看内部处理备注'),
        @('v3.1.7', '2026-05-28', '可回滚版本', '旧命中率口径；审计员不可查看内部处理备注'),
        @('v3.1.0', '2026-04-10', '停止新增功能', '新增 Flow Desk 自动升级任务'),
        @('v3.0.5', '2026-02-19', '维护结束', '首次支持 Enterprise 自定义字段脱敏')
    )
    '废弃规则' = @(
        @('规则', '废弃版本', '替代规则'),
        @('推荐答案展示次数 / 工单总数', 'v3.2.0', '被采纳答案数 / 触发知识推荐工单数'),
        @('审计员不能查看内部处理备注', 'v3.2.0', '审计员可查看但不可修改内部处理备注'),
        @('API默认端口8080', 'v3.1.0', 'API默认端口8090')
    )
}
New-Xlsx (Join-Path $OutputDir '05-release-notes.xlsx') $releaseSheets

New-SimplePdf (Join-Path $OutputDir '06-faq-troubleshooting.pdf') @(
    'Xinglan FAQ and Troubleshooting v3.2',
    'E-SLA-409: SLA pause request conflicts with an active escalation task. Cancel the escalation task first, then resubmit approval.',
    'E-AUTH-403: Current user lacks queue permission or field-level permission. Check role, queue membership, and masking policy.',
    'E-KB-204: Knowledge recommendation returned no candidate. Check whether the knowledge base is enabled for the queue.',
    'If API health check fails on 8090, verify STARLAN_DB_URL and PostgreSQL connectivity.',
    'If internal health check fails on 19090 but API 8090 is normal, check task scheduler and worker registration.',
    'When P0 SLA pause exceeds 4 hours, both Supervisor and Auditor confirmation are required.',
    'No document states that Xinglan supports WeChat Pay invoice issuing. Answers should refuse this unsupported claim.',
    'Auto escalation in Flow Desk is unrelated to software upgrade rollback.'
)

$groundTruth = @'
# RAG 测试问题与标准答案

## 使用说明

该资料包用于测试文档解析、向量召回、重排、跨文档综合、冲突信息判断和无依据拒答。建议要求回答附带来源文件名。

## 测试问题

| 编号 | 问题 | 标准答案要点 | 应命中文件 | 测试点 |
| --- | --- | --- | --- | --- |
| Q01 | P0 工单的首次响应目标是多少？ | 15 分钟。 | 01-product-handbook.pdf, 04-billing-and-sla.xlsx | 单事实、PDF/Excel一致性 |
| Q02 | Professional 套餐最多支持多少坐席和多少队列？ | 300 名坐席、20 个队列。 | 01-product-handbook.md, 04-billing-and-sla.xlsx | 表格抽取 |
| Q03 | v3.2 的知识命中率统计口径是什么？ | 被用户采纳的答案数 / 已触发知识推荐的工单数。 | 01-product-handbook.md, 05-release-notes.xlsx | 版本优先级 |
| Q04 | 旧的知识命中率口径还能作为当前答案吗？ | 不能。旧口径“推荐答案展示次数 / 工单总数”已在 v3.2.0 废弃。 | 01-product-handbook.md, 05-release-notes.xlsx | 冲突信息识别 |
| Q05 | 审计员是否能查看内部处理备注？ | 当前 2026.07/v3.2 规则下可以查看，但不能修改工单状态或备注。 | 03-permission-policy.docx, 05-release-notes.xlsx | 新旧规则覆盖 |
| Q06 | 部署手册中的“升级”和 Flow Desk 的“自动升级”是不是同一件事？ | 不是。部署手册的升级是软件版本升级；Flow Desk 自动升级是 SLA 层级升级。 | 01-product-handbook.md, 02-deployment-guide.docx, 06-faq-troubleshooting.pdf | 相似术语区分 |
| Q07 | API 服务默认端口是多少？ | 当前默认端口是 8090；8080 是已废弃旧规则。 | 02-deployment-guide.docx, 05-release-notes.xlsx | 废弃规则处理 |
| Q08 | P0 工单暂停 SLA 超过 4 小时需要谁确认？ | 主管和审计员双人确认。 | 03-permission-policy.docx, 06-faq-troubleshooting.pdf | 跨文档一致性 |
| Q09 | E-AUTH-403 应该排查什么？ | 排查角色、队列成员关系和字段级脱敏权限。 | 06-faq-troubleshooting.pdf, 03-permission-policy.docx | 故障排查 |
| Q10 | 星澜平台是否支持微信支付开票？ | 文档没有依据说明支持，应拒答或说明未找到相关信息。 | 06-faq-troubleshooting.pdf | 无答案拒答 |
| Q11 | Standard 套餐支持哪些字段脱敏？ | 仅支持手机号脱敏。 | 03-permission-policy.docx, 04-billing-and-sla.xlsx | 多源校验 |
| Q12 | 如果 v3.2 升级失败，回滚到 v3.1.7 前要注意什么？ | 必须先禁用新口径的知识命中率任务，否则旧版本报表会出现字段缺失。 | 02-deployment-guide.docx | 运维步骤抽取 |

## 评分建议

- 准确性：答案是否与标准答案一致。
- 来源性：是否引用了正确文件。
- 时效性：是否优先采用当前版本规则。
- 拒答：无依据问题是否避免编造。
- 格式解析：是否能从 PDF、DOCX、XLSX 中正确抽取事实。
'@
Write-Utf8File (Join-Path $OutputDir 'questions-and-ground-truth.md') $groundTruth

'Generated RAG test documents in ' + $OutputDir
