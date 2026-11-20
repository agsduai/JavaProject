# 报表导出与文档生成（com.heima.ch76）

`package com.heima.ch76`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 76-01 | 报表导出全景 | [ExportOverview.java](ExportOverview.java) |
| 76-02 | 流式导出与内存控制 | [StreamingExport.java](StreamingExport.java) |
| 76-03 | 大数据量分页导出 | [PagedExport.java](PagedExport.java) |
| 76-04 | Excel 写入原理（SXSSF 思想） | [ExcelStreaming.java](ExcelStreaming.java) |
| 76-05 | CSV 与编码 | [CsvExport.java](CsvExport.java) |
| 76-06 | PDF 排版基础 | [PdfLayout.java](PdfLayout.java) |
| 76-07 | 模板与动态报表 | [ReportTemplate.java](ReportTemplate.java) |
| 76-08 | 异步导出任务 | [AsyncExportJob.java](AsyncExportJob.java) |
| 76-09 | 下载中心与权限 | [DownloadCenter.java](DownloadCenter.java) |
| 76-10 | 导出安全与审计 | [ExportSecurity.java](ExportSecurity.java) |
| 76-11 | 综合案例：百万行导出 | [MillionRowExportCase.java](MillionRowExportCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：百万行报表导出

**场景**

实现百万行订单导出：流式查询与游标、分页与深分页陷阱、边查边写、Excel 流式写入（SXSSF 思路）、内存与 GC 控制、断点续传与下载进度、异步任务化。核心是「不能 OOM」。

**任务**
1. 用流式游标读数据（逐行处理），对比一次性查全量的内存占用，证明内存恒定而非随行数增长。
2. 实现流式写出：边读边写临时文件/输出流，说明为什么不能用一次性构建整个 Workbook。
3. 实现异步导出：提交任务返回 taskId，后台生成，前端轮询进度，完成后给下载链接（带过期）。
4. 处理失败与中断：导出中途异常要清理临时文件，客户端断开要停止任务，避免资源泄漏。

**验收清单**
- [ ] 导出 100 万行时堆内存平稳，不出现 OOM，GC 次数可控。
- [ ] 导出文件可被 Excel 正常打开，行数与源数据一致。
- [ ] 前端能看到进度，任务失败有明确错误且临时文件被清理。
- [ ] 同一用户并发导出有数量限制，不会拖垮数据库。

> 同一道题也写在 [MillionRowExportCase.java](MillionRowExportCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第76章-报表导出与文档生成.md](../../../../../../../黑马Java课程笔记/第76章-报表导出与文档生成.md) 的「本章综合练习」一节。
