# 出货单 ESC/P 打印模块（Fujitsu DPK700H）

独立 Spring Boot + Vue 模块：后端按字符网格生成 **ESC/P（GBK）** 原语，经 Windows 原始打印送到针式机；Vue 负责录入、等宽预览与触发打印。后续可拷贝进业务 Spring Boot + Vue 工程。

## 目录

```
shipping-order-print/
  backend/     Spring Boot 3 / Java 17
  frontend/    Vue 3 + Vite
  README.md
```

## 打印机前提（DPK700H）

1. USB 连接并开机。设备管理器中应出现 **NFCP DPK700**（VID_04C5）。仅有 USB 设备、没有打印队列时，Windows「打印机和扫描仪」可能仍为空。
2. 本仓库已提供建队列脚本（管理员 PowerShell）：

```powershell
cd C:\Users\Rachel\IdeaProjects\shipping-order-print
powershell -ExecutionPolicy Bypass -File .\scripts\setup-dpk700-printer.ps1
```

脚本会在端口 `USB005` 上创建队列 **NFCP DPK700**（临时用 Microsoft IPP Class Driver）。Datatype 为 RAW，可供 ESC/P 原语提交。
3. **强烈建议**再安装富士通 DPK700H 官方驱动，把该队列的驱动换成官方型号，面板仿真切到 **LQ1600K+**。
4. `application.yml` 默认：

```yaml
print:
  printer-name: "NFCP DPK700"
```

前端流程：**打印预览 → 确认后才发送到所选打印机**（不会自动打到系统默认机 / OneNote / PDF）。

## 编码与指令

- 文本编码：**GBK**（机内 GB18030 字库兼容）。
- 初始化：`ESC @`，汉字模式：`FS &`。
- 行距：`ESC 3 n`（`print.page.line-spacing-n`，单位 1/180 英寸）。
- 左边距：`ESC $` 绝对水平定位（`print.page.left-margin` 按半角字符估算）。
- 版式列宽：`print.table.col-*`，现场套打只调 yml，不改业务代码。

## 启动

### 后端

本机若未安装 Maven，请用项目自带的 Wrapper（推荐）：

```powershell
cd C:\Users\Rachel\IdeaProjects\shipping-order-print\backend
.\mvnw.cmd spring-boot:run
```

或直接调用本仓库已下载的 Maven：

```powershell
cd C:\Users\Rachel\IdeaProjects\shipping-order-print\backend
..\\.tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
```

若已全局安装 Maven，也可：`mvn spring-boot:run`。

默认 `http://localhost:8080`。

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认 `http://localhost:5173`（Vite 已代理 `/api` → `8080`）。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/printers` | 列出本机打印机 |
| POST | `/api/print/shipping-order` | 生成 ESC/P 并原始打印；可选 `?printerName=` |
| POST | `/api/print/shipping-order/preview` | 返回等宽纯文本预览 |
| POST | `/api/print/shipping-order/preview-bytes` | 下载 `.bin` 指令流（不占纸） |
| POST | `/api/print/shipping-order/preview-bytes/json` | Base64 / Hex + 文本预览 |

请求体示例：

```json
{
  "companyTitle": "怀化市兴隆农业开发有限公司出货单",
  "customerName": "本部食堂",
  "orderNo": "XS-202607010206",
  "date": "2026-07-01",
  "deliverer": "",
  "receiver": "周高玉",
  "lines": [
    {
      "productName": "牛兼",
      "unit": "公斤",
      "quantity": 4,
      "unitPrice": 70.00,
      "remark": ""
    }
  ]
}
```

## 校准建议

1. 先点「导出指令」得到 `shipping-order.escp.bin`，确认能生成。
2. 用空白纸点「打印」，对照样单微调：
   - `print.page.left-margin` — 整体左右
   - `print.page.line-spacing-n` — 行距/上下疏密
   - `print.table.col-*` — 表列对齐
   - `print.page.cols` — 总列宽（默认 80）
3. 若汉字乱码：确认仿真为 LQ1600K+，且 `print.encoding` 为 `GBK`。
4. 若驱动吞掉原语（变成图形打印错位）：在驱动中开启「直通/RAW」或使用支持 raw 的队列；也可用共享打印机名再试。

## 迁入主项目清单

1. 拷贝 Java 包：
   - `com.xinglong.print.print`（含 `escp`）
   - `com.xinglong.print.config.PrintProperties`
   - `com.xinglong.print.web`（DTO + Controller，可按需改包名）
2. 合并 `application.yml` 中 `print.*` 配置。
3. 前端拷贝出货单页与 `api.js` 中的打印调用，或把业务订单 DTO 映射为 `ShippingOrderPrintRequest` 后调同一 API。
4. 无需改 ESC/P 内核；仅做字段映射与权限/鉴权。

## 核心类

- `EscpCommandWriter` — ESC/P 字节组装
- `ShippingOrderEscpBuilder` — 出货单字符网格套打
- `AmountToChinese` — 金额大写
- `RawPrintService` — `DocFlavor.BYTE_ARRAY.AUTOSENSE` 原始提交
