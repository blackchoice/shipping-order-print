# 克隆 CRMEB Java 商城并集成打印功能

$ErrorActionPreference = "Stop"

Write-Host "=== CRMEB Java 商城 - 克隆与集成打印功能 ===" -ForegroundColor Cyan
Write-Host ""

# 配置
$targetDir = "D:\IdeaProjects"
$projectName = "crmeb-java-deploy"
$projectPath = Join-Path $targetDir $projectName
$gitUrl = "https://github.com/blackchoice/crmeb-java-deploy.git"
$printModulePath = "C:\Users\Rachel\IdeaProjects\shipping-order-print"

# 步骤 1：创建目录
Write-Host "[1/5] 准备目录..." -ForegroundColor Yellow
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    Write-Host "✓ 创建目录: $targetDir" -ForegroundColor Green
} else {
    Write-Host "✓ 目录已存在: $targetDir" -ForegroundColor Green
}

# 步骤 2：克隆项目
Write-Host ""
Write-Host "[2/5] 克隆 CRMEB Java 项目..." -ForegroundColor Yellow
Write-Host "从: $gitUrl" -ForegroundColor Gray
Write-Host "到: $projectPath" -ForegroundColor Gray

if (Test-Path $projectPath) {
    Write-Host "项目目录已存在，是否删除重新克隆？(y/n)" -ForegroundColor Yellow
    $confirm = Read-Host
    if ($confirm -eq 'y' -or $confirm -eq 'Y') {
        Remove-Item $projectPath -Recurse -Force
        Write-Host "✓ 已删除旧项目" -ForegroundColor Green
    } else {
        Write-Host "使用现有项目" -ForegroundColor Gray
    }
}

if (-not (Test-Path $projectPath)) {
    Set-Location $targetDir
    git clone $gitUrl

    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ 克隆失败" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ 克隆完成" -ForegroundColor Green
} else {
    Write-Host "✓ 使用现有项目" -ForegroundColor Green
}

# 步骤 3：分析项目结构
Write-Host ""
Write-Host "[3/5] 分析 CRMEB 项目结构..." -ForegroundColor Yellow

Set-Location $projectPath

$projectStructure = Get-ChildItem -Directory | Select-Object Name
Write-Host "项目目录:" -ForegroundColor Gray
$projectStructure | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Gray }

# CRMEB 是 Java 后端 + Vue 前端分离项目
$backendPath = Join-Path $projectPath "crmeb"
$frontendPath = Join-Path $projectPath "admin"

if (Test-Path $backendPath) {
    Write-Host "✓ 找到后端目录: $backendPath" -ForegroundColor Green
}
if (Test-Path $frontendPath) {
    Write-Host "✓ 找到前端目录: $frontendPath" -ForegroundColor Green
}

# 步骤 4：集成打印模块到后端
Write-Host ""
Write-Host "[4/5] 集成打印模块到 CRMEB 后端..." -ForegroundColor Yellow

# 4.1 复制后端 Java 打印模块
$printJavaSource = Join-Path $printModulePath "backend\src\main\java\com\xinglong\print"
$printJavaDest = Join-Path $backendPath "crmeb-service\src\main\java\com\zbkj\service\print"

if (Test-Path $backendPath) {
    Write-Host "复制打印 Java 模块..." -ForegroundColor Gray

    if (-not (Test-Path $printJavaDest)) {
        New-Item -ItemType Directory -Path $printJavaDest -Force | Out-Null
    }

    Copy-Item -Path "$printJavaSource\*" -Destination $printJavaDest -Recurse -Force
    Write-Host "✓ 已复制 Java 打印模块" -ForegroundColor Green

    # 4.2 复制配置文件
    $printYmlSource = Join-Path $printModulePath "backend\src\main\resources\application.yml"
    Write-Host "提示: 需要手动合并打印配置到 CRMEB 的 application.yml" -ForegroundColor Yellow

    # 4.3 添加 CORS 配置
    $corsConfigDest = Join-Path $backendPath "crmeb-admin\src\main\java\com\zbkj\admin\config\CorsConfig.java"
    Copy-Item -Path (Join-Path $printModulePath "backend\src\main\java\com\xinglong\print\config\CorsConfig.java") `
              -Destination $corsConfigDest -Force
    Write-Host "✓ 已添加 CORS 配置" -ForegroundColor Green
}

# 步骤 5：集成打印功能到前端
Write-Host ""
Write-Host "[5/5] 集成打印功能到 CRMEB 前端..." -ForegroundColor Yellow

if (Test-Path $frontendPath) {
    # 5.1 复制 JavaScript 客户端库
    $jsClientSource = Join-Path $printModulePath "docs\商城集成-JavaScript客户端.js"
    $jsClientDest = Join-Path $frontendPath "src\utils\shipping-print-client.js"

    Copy-Item $jsClientSource -Destination $jsClientDest -Force
    Write-Host "✓ 已复制打印客户端库到: $jsClientDest" -ForegroundColor Green

    # 5.2 查找订单详情页面
    Write-Host ""
    Write-Host "查找订单相关页面..." -ForegroundColor Gray
    $orderPages = Get-ChildItem -Path $frontendPath -Recurse -File -Include *.vue |
        Where-Object { $_.Name -match '(order|订单).*detail' }

    if ($orderPages) {
        Write-Host "找到订单详情页面:" -ForegroundColor Green
        $orderPages | ForEach-Object { Write-Host "  - $($_.FullName)" -ForegroundColor Gray }
    } else {
        Write-Host "未找到明确的订单详情页面，搜索所有订单相关页面..." -ForegroundColor Yellow
        $orderPages = Get-ChildItem -Path $frontendPath -Recurse -File -Include *.vue |
            Where-Object { $_.Name -match 'order' }
        $orderPages | Select-Object -First 10 | ForEach-Object { Write-Host "  - $($_.FullName)" -ForegroundColor Gray }
    }
}

# 生成集成指南
Write-Host ""
Write-Host "生成集成指南..." -ForegroundColor Yellow

$integrationGuide = @"
# CRMEB Java 商城 - 打印功能集成指南

## 项目信息

- 项目路径: $projectPath
- 后端: $backendPath
- 前端: $frontendPath
- 技术栈: Spring Boot + Vue.js

## 已完成的工作

### 后端集成

1. ✅ 已复制打印模块到:
   ``````
   $printJavaDest
   ``````

2. ✅ 已添加 CORS 配置到:
   ``````
   $corsConfigDest
   ``````

### 前端集成

1. ✅ 已复制打印客户端库到:
   ``````
   $jsClientDest
   ``````

## 需要手动完成的步骤

### 步骤 1：配置后端

编辑 CRMEB 的 application.yml，添加打印配置：

``````yaml
# 打印配置
print:
  printer-name: "NFCP DPK700"
  encoding: GBK
  page:
    width-mm: 241
    height-mm: 93
    cpi: 12
    cols: 100
    left-margin-mm: 16
    line-spacing-n: 20
  table:
    col-index: 10
    col-name: 28
    col-unit: 8
    col-qty: 10
    col-price: 12
    col-amount: 14
    col-remark: 20
    max-rows: 7
``````

### 步骤 2：添加打印 Controller

在后端创建打印控制器或集成到现有订单 Controller：

``````java
package com.zbkj.admin.controller;

import com.zbkj.service.print.ShippingOrderPrintService;
import com.zbkj.service.print.dto.ShippingOrderPrintRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/print")
public class PrintController {

    @Autowired
    private ShippingOrderPrintService printService;

    @GetMapping("/printers")
    public Map<String, Object> listPrinters() {
        List<String> printers = printService.listPrinters();
        return Map.of("printers", printers);
    }

    @PostMapping("/shipping-order")
    public Map<String, String> printShippingOrder(
            @RequestBody ShippingOrderPrintRequest request,
            @RequestParam(required = false) String printerName) {
        printService.print(request, printerName);
        return Map.of("message", "打印成功");
    }

    @PostMapping("/shipping-order/layout")
    public Map<String, Object> previewLayout(@RequestBody ShippingOrderPrintRequest request) {
        return printService.previewLayout(request);
    }
}
``````

### 步骤 3：修改前端订单详情页面

找到订单详情页面（通常在 `src/views/order/` 目录），添加打印功能：

#### 3.1 引入打印客户端

在 Vue 组件的 `<script>` 部分添加：

``````javascript
import ShippingPrintClient from '@/utils/shipping-print-client.js'
``````

#### 3.2 添加打印按钮

在订单详情页面的操作按钮区域添加：

``````html
<template>
  <div class="order-detail">
    <!-- 现有订单信息 -->

    <div class="action-buttons">
      <!-- 现有按钮 -->

      <!-- 新增打印按钮 -->
      <el-button
        type="primary"
        icon="el-icon-printer"
        @click="handlePrint"
        :loading="printing">
        打印出货单
      </el-button>
    </div>

    <!-- 打印状态提示 -->
    <el-alert
      v-if="printStatus"
      :title="printStatus"
      :type="printSuccess ? 'success' : 'error'"
      :closable="true"
      @close="printStatus = ''"
      style="margin-top: 10px;">
    </el-alert>
  </div>
</template>
``````

#### 3.3 添加打印方法

在 Vue 组件的 `methods` 中添加：

``````javascript
export default {
  data() {
    return {
      orderDetail: {},  // 订单详情
      printing: false,
      printStatus: '',
      printSuccess: false
    }
  },

  methods: {
    async handlePrint() {
      this.printing = true
      this.printStatus = ''

      try {
        // 转换订单数据为打印格式
        const printData = {
          companyTitle: '怀化市兴隆农业开发有限公司出货单',
          customerName: this.orderDetail.realName || this.orderDetail.userName,
          orderNo: this.orderDetail.orderId,
          date: this.orderDetail.createTime.split(' ')[0],
          deliverer: '',
          receiver: this.orderDetail.realName,
          items: this.orderDetail.orderInfoList.map(item => ({
            productName: item.productName,
            unit: item.unit || '件',
            quantity: item.payNum,
            unitPrice: item.price,
            remark: ''
          }))
        }

        // 调用打印
        const result = await ShippingPrintClient.printOrder(printData)

        this.printStatus = result.success
          ? `打印成功 → ${result.printerName}`
          : result.message
        this.printSuccess = result.success

      } catch (error) {
        this.printStatus = `打印失败: ${error.message}`
        this.printSuccess = false
      } finally {
        this.printing = false
      }
    }
  }
}
``````

### 步骤 4：配置 Vue 代理（开发环境）

如果需要在开发环境测试，编辑 `vue.config.js` 添加代理：

``````javascript
module.exports = {
  devServer: {
    proxy: {
      '/api/admin/print': {
        target: 'http://localhost:8080',  // CRMEB 后端端口
        changeOrigin: true
      }
    }
  }
}
``````

### 步骤 5：启动并测试

#### 5.1 启动本地打印服务

``````powershell
C:\Users\Rachel\IdeaProjects\shipping-order-print\scripts\start-print-service.bat
``````

#### 5.2 启动 CRMEB 后端

``````powershell
cd $backendPath
# 根据 CRMEB 启动方式运行
``````

#### 5.3 启动 CRMEB 前端

``````powershell
cd $frontendPath
npm install
npm run serve
``````

#### 5.4 测试打印

1. 登录 CRMEB 后台
2. 进入订单管理 → 订单详情
3. 点击"打印出货单"按钮
4. 查看本地打印机是否打印

## 部署到生产环境

### 后端部署

1. 打包 CRMEB 后端：
   ``````bash
   cd $backendPath
   mvn clean package -DskipTests
   ``````

2. 上传到服务器 119.29.98.147

### 前端部署

1. 构建前端：
   ``````bash
   cd $frontendPath
   npm run build
   ``````

2. 将 dist 目录上传到服务器

### 配置 Nginx

在服务器 Nginx 配置中添加打印 API 代理：

``````nginx
location /api/admin/print/ {
    proxy_pass http://localhost:8080/api/admin/print/;
    proxy_set_header Host `$host;
    proxy_set_header X-Real-IP `$remote_addr;
}
``````

## 注意事项

1. **包名调整**: 打印模块的包名可能需要从 `com.xinglong.print` 改为 `com.zbkj.service.print` 以匹配 CRMEB 的包结构

2. **依赖项**: 确保 CRMEB 的 pom.xml 包含必要的依赖（spring-boot-starter-web 等）

3. **权限控制**: 根据 CRMEB 的权限系统，可能需要为打印功能添加权限配置

4. **数据映射**: 订单数据字段名可能与示例不同，需要根据 CRMEB 实际的订单实体调整

## 参考文档

- 完整集成示例: C:\Users\Rachel\IdeaProjects\shipping-order-print\docs\商城集成-完整示例.html
- JavaScript 客户端文档: C:\Users\Rachel\IdeaProjects\shipping-order-print\docs\README.md
- 部署指南: C:\Users\Rachel\IdeaProjects\shipping-order-print\docs\deployment-guide.md

## 故障排查

### 问题：找不到打印服务

确保本地 Windows 已启动打印服务，访问 http://localhost:8080/api/printers 验证。

### 问题：CORS 错误

检查后端 CorsConfig.java 中是否包含前端地址。

### 问题：订单数据格式不匹配

根据 CRMEB 实际订单实体调整数据映射逻辑。
"@

$integrationGuide | Out-File -FilePath (Join-Path $projectPath "打印功能集成指南.md") -Encoding UTF8

Write-Host "✓ 已生成集成指南: $projectPath\打印功能集成指南.md" -ForegroundColor Green

# 完成
Write-Host ""
Write-Host "=== 克隆和集成完成 ===" -ForegroundColor Green
Write-Host ""
Write-Host "项目位置: $projectPath" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步操作:" -ForegroundColor Yellow
Write-Host "1. 阅读集成指南: $projectPath\打印功能集成指南.md" -ForegroundColor White
Write-Host "2. 找到订单详情页面并添加打印按钮" -ForegroundColor White
Write-Host "3. 配置后端 application.yml" -ForegroundColor White
Write-Host "4. 启动项目并测试打印功能" -ForegroundColor White
Write-Host ""
Write-Host "需要帮助？运行以下命令查看集成指南:" -ForegroundColor Cyan
Write-Host "notepad `"$projectPath\打印功能集成指南.md`"" -ForegroundColor Gray
Write-Host ""

# 打开集成指南
$openGuide = Read-Host "是否立即打开集成指南？(y/n)"
if ($openGuide -eq 'y' -or $openGuide -eq 'Y') {
    notepad "$projectPath\打印功能集成指南.md"
}
