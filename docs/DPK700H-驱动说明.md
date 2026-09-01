# DPK700H 驱动安装（解决 Offline / 打不出纸）

## 已确认现象

- USB 已识别：`NFCP DPK700`（`VID_04C5&PID_106F`），端口 `USB005`
- 临时队列使用 **Microsoft IPP Class Driver** 时：任务会变成 `Printing, Offline`，针式机无法正常出纸
- 程序已改为 **Win32 RAW** 提交（`scripts/raw-print.ps1`），不再走 `javax.print` AUTOSENSE

## 你需要做的（必须）

1. 下载 **DPK700/700H/700K** 官方 Windows 驱动（南京富电 / 富士通 DPK 系列通用包），解压后运行 `setup.exe`  
   - 型号可选：`DPK700K` / `DPK700H`（同系列兼容）  
   - 参考下载入口：http://www.nfet.net.cn/downlist/ （若打不开，用驱动光盘或经销商提供的包）
2. 安装完成后打开「打印机和扫描仪」：
   - 确认出现富士通/DPK 队列，或把现有 **NFCP DPK700** 的驱动换成官方驱动
   - 端口保持 **USB005**
3. 打印机面板仿真切到 **LQ1600K+**，并把 **AUTO LF（自动换行）关闭**
   - 程序按 `line-ending: crlf` 发送回车+换行；若 AUTO LF 开着，打印机会自己再补一次换行，
     每行走两行，单据会溢到下一张纸
   - 不想动打印机开关时，把 `application.yml` 的 `line-ending` 改成 `lf` 即可
4. 装好纸、色带，取消队列里卡住的任务
5. **重启后端**：

```powershell
cd C:\Users\Rachel\IdeaProjects\shipping-order-print\backend
..\..\.tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
# 或
.\mvnw.cmd spring-boot:run
```

（路径以你机器为准：`..\.tools\apache-maven-3.9.9\bin\mvn.cmd`）

6. 前端刷新 → 打印预览 → 选择 **NFCP DPK700**（或官方驱动显示的名称）→ 确认发送

## 清卡住任务

```powershell
Get-PrintJob -PrinterName 'NFCP DPK700' | Remove-PrintJob -Confirm:$false
```

## 验证 RAW 脚本

```powershell
# 先准备一个 bin，或用前端「导出指令」
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\raw-print.ps1 -PrinterName "NFCP DPK700" -FilePath .\shipping-order.escp.bin
```

若官方驱动安装后队列名变了，把 `application.yml` 的 `print.printer-name` 改成新名称，并在前端下拉里选同一台。
