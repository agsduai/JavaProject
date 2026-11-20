# 文件服务与对象存储工程（com.heima.ch73）

`package com.heima.ch73`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 73-01 | 文件服务全景：上传、存储、下载 | [FileServiceOverview.java](FileServiceOverview.java) |
| 73-02 | 分片上传与断点续传 | [MultipartUpload.java](MultipartUpload.java) |
| 73-03 | 秒传与内容去重 | [InstantUpload.java](InstantUpload.java) |
| 73-04 | 存储选型：本地盘、对象存储与 CDN | [StorageChoice.java](StorageChoice.java) |
| 73-05 | 上传安全：类型、大小与内容校验 | [UploadSecurity.java](UploadSecurity.java) |
| 73-06 | 防盗链与签名 URL | [SignedUrl.java](SignedUrl.java) |
| 73-07 | 图片处理与动态缩放 | [ImageProcessing.java](ImageProcessing.java) |
| 73-08 | 大文件下载与断点下载 | [RangeDownload.java](RangeDownload.java) |
| 73-09 | 大文件导出与压缩 | [ExportAndZip.java](ExportAndZip.java) |
| 73-10 | 存储治理：生命周期与成本 | [StorageGovernance.java](StorageGovernance.java) |
| 73-11 | 综合案例：文件中台 | [FileHubCase.java](FileHubCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：文件服务的上传与分发

**场景**

做一个文件服务：分片上传与断点续传、秒传（内容哈希）、大文件合并、直传对象存储与预签名、防盗链、图片处理、生命周期与冷热分层。要能回答「10GB 文件怎么传」。

**任务**
1. 实现分片上传：文件切片、并发上传、服务端记录分片状态，中断后从已上传分片续传。
2. 实现秒传：先算文件 SHA-256，命中已有文件直接返回引用，说明哈希碰撞与权限隔离的注意点。
3. 实现预签名直传：服务端只发临时凭证，客户端直传对象存储，说明为什么这样能减轻服务端带宽压力。
4. 实现访问控制：私有文件用带过期时间的签名 URL、公开文件走 CDN + 防盗链（Referer/时间戳签名）。

**验收清单**
- [ ] 上传中断后重连续传，只补传缺失分片。
- [ ] 相同内容重复上传秒传命中，不同用户之间不串文件。
- [ ] 直传链路服务端不中转文件流，带宽占用可验证下降。
- [ ] 私有文件未签名不可访问，签名过期后自动失效。

> 同一道题也写在 [FileHubCase.java](FileHubCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第73章-文件服务与对象存储工程.md](../../../../../../../黑马Java课程笔记/第73章-文件服务与对象存储工程.md) 的「本章综合练习」一节。
