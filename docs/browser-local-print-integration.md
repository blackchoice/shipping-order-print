# 浏览器调用本地打印服务集成方案

## 架构说明

```
用户浏览器
    ↓ HTTPS/HTTP
商城服务器 (119.29.98.147:8899)
    - 提供订单数据
    - 提供打印页面

用户浏览器（同一个浏览器窗口）
    ↓ HTTP (localhost)
本地打印服务 (127.0.0.1:8080)
    ↓ 直接打印
本地打印机 (NFCP DPK700)
```

**关键点**：浏览器可以同时访问远程商城和本地打印服务，不需要服务器和本地互相访问。

---

## 步骤 1：在本地启动打印服务

### 修改配置允许 CORS

编辑 `backend/src/main/java/com/xinglong/print/config/CorsConfig.java`（新建）：

```java
package com.xinglong.print.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://119.29.98.147:8899", "http://localhost:8899")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
    }
}
```

### 启动服务

```powershell
cd C:\Users\Rachel\IdeaProjects\shipping-order-print\backend

# 方式 1：开发模式
.\mvnw.cmd spring-boot:run

# 方式 2：打包后运行（推荐）
.\mvnw.cmd clean package -DskipTests
java -jar target\shipping-order-print-1.0.0-SNAPSHOT.jar
```

### 配置防火墙

```powershell
New-NetFirewallRule -DisplayName "Shipping Print Service" `
  -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

### 验证服务

```powershell
curl http://localhost:8080/api/printers
```

---

## 步骤 2：商城系统集成

### 方案 A：在商城订单详情页添加打印按钮

在商城的订单详情页面（HTML/JSP/Vue/React 等）添加打印功能。

#### HTML + JavaScript 示例

```html
<!-- 商城订单详情页 -->
<div class="order-detail">
  <h2>订单详情</h2>
  <div>订单号：<span id="orderNo">XS-202607010206</span></div>
  <div>客户：<span id="customerName">本部食堂</span></div>
  
  <table id="orderItems">
    <thead>
      <tr><th>商品名称</th><th>单位</th><th>数量</th><th>单价</th></tr>
    </thead>
    <tbody>
      <tr>
        <td>牛腩</td>
        <td>公斤</td>
        <td>4</td>
        <td>70.00</td>
      </tr>
    </tbody>
  </table>
  
  <button onclick="printShippingOrder()">打印出货单</button>
  <div id="printStatus"></div>
</div>

<script>
// 本地打印服务地址
const PRINT_SERVICE_URL = 'http://localhost:8080';

async function printShippingOrder() {
  const statusEl = document.getElementById('printStatus');
  statusEl.textContent = '正在打印...';
  
  try {
    // 1. 检查本地打印服务是否可用
    const printersResp = await fetch(`${PRINT_SERVICE_URL}/api/printers`);
    if (!printersResp.ok) {
      throw new Error('本地打印服务未启动。请确保打印服务正在运行。');
    }
    
    const printersData = await printersResp.json();
    const printers = printersData.printers || [];
    
    // 2. 选择打印机（自动选择 DPK700，或让用户选择）
    let printerName = printers.find(p => /DPK700|NFCP/i.test(p));
    if (!printerName && printers.length > 0) {
      printerName = printers[0]; // 使用第一个打印机
    }
    if (!printerName) {
      throw new Error('未找到可用的打印机');
    }
    
    // 3. 准备打印数据（从页面提取订单信息）
    const orderData = {
      companyTitle: '怀化市兴隆农业开发有限公司出货单',
      customerName: document.getElementById('customerName').textContent,
      orderNo: document.getElementById('orderNo').textContent,
      date: new Date().toISOString().split('T')[0],
      deliverer: '',
      receiver: '',
      lines: []
    };
    
    // 从表格提取商品明细
    const rows = document.querySelectorAll('#orderItems tbody tr');
    rows.forEach(row => {
      const cells = row.querySelectorAll('td');
      orderData.lines.push({
        productName: cells[0].textContent,
        unit: cells[1].textContent,
        quantity: parseFloat(cells[2].textContent) || 0,
        unitPrice: parseFloat(cells[3].textContent) || 0,
        remark: ''
      });
    });
    
    // 4. 发送打印请求
    const printResp = await fetch(
      `${PRINT_SERVICE_URL}/api/print/shipping-order?printerName=${encodeURIComponent(printerName)}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData)
      }
    );
    
    if (!printResp.ok) {
      const error = await printResp.json();
      throw new Error(error.message || '打印失败');
    }
    
    const result = await printResp.json();
    statusEl.textContent = `✓ ${result.message || '打印成功'} → ${printerName}`;
    statusEl.style.color = 'green';
    
  } catch (error) {
    statusEl.textContent = `✗ ${error.message}`;
    statusEl.style.color = 'red';
    console.error('打印错误:', error);
  }
}
</script>
```

#### Vue.js 示例

```vue
<template>
  <div class="order-detail">
    <h2>订单详情</h2>
    <div>订单号：{{ order.orderNo }}</div>
    <div>客户：{{ order.customerName }}</div>
    
    <button @click="printShippingOrder" :disabled="printing">
      {{ printing ? '打印中...' : '打印出货单' }}
    </button>
    <p v-if="printStatus" :class="printSuccess ? 'success' : 'error'">
      {{ printStatus }}
    </p>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const PRINT_SERVICE_URL = 'http://localhost:8080'

const order = ref({
  orderNo: 'XS-202607010206',
  customerName: '本部食堂',
  items: [
    { productName: '牛腩', unit: '公斤', quantity: 4, unitPrice: 70 }
  ]
})

const printing = ref(false)
const printStatus = ref('')
const printSuccess = ref(false)

async function printShippingOrder() {
  printing.value = true
  printStatus.value = ''
  
  try {
    // 检查打印服务
    const printersResp = await fetch(`${PRINT_SERVICE_URL}/api/printers`)
    if (!printersResp.ok) {
      throw new Error('本地打印服务未启动')
    }
    
    const printersData = await printersResp.json()
    let printerName = printersData.printers.find(p => /DPK700|NFCP/i.test(p))
    if (!printerName && printersData.printers.length > 0) {
      printerName = printersData.printers[0]
    }
    
    // 准备数据
    const orderData = {
      companyTitle: '怀化市兴隆农业开发有限公司出货单',
      customerName: order.value.customerName,
      orderNo: order.value.orderNo,
      date: new Date().toISOString().split('T')[0],
      deliverer: '',
      receiver: '',
      lines: order.value.items.map(item => ({
        productName: item.productName,
        unit: item.unit,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        remark: ''
      }))
    }
    
    // 打印
    const printResp = await fetch(
      `${PRINT_SERVICE_URL}/api/print/shipping-order?printerName=${encodeURIComponent(printerName)}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData)
      }
    )
    
    if (!printResp.ok) {
      const error = await printResp.json()
      throw new Error(error.message || '打印失败')
    }
    
    const result = await printResp.json()
    printStatus.value = `${result.message} → ${printerName}`
    printSuccess.value = true
    
  } catch (error) {
    printStatus.value = error.message
    printSuccess.value = false
  } finally {
    printing.value = false
  }
}
</script>

<style scoped>
.success { color: green; }
.error { color: red; }
</style>
```

#### React 示例

```jsx
import { useState } from 'react'

const PRINT_SERVICE_URL = 'http://localhost:8080'

export default function OrderDetail({ order }) {
  const [printing, setPrinting] = useState(false)
  const [printStatus, setPrintStatus] = useState('')
  const [printSuccess, setPrintSuccess] = useState(false)
  
  async function printShippingOrder() {
    setPrinting(true)
    setPrintStatus('')
    
    try {
      const printersResp = await fetch(`${PRINT_SERVICE_URL}/api/printers`)
      if (!printersResp.ok) {
        throw new Error('本地打印服务未启动')
      }
      
      const printersData = await printersResp.json()
      let printerName = printersData.printers.find(p => /DPK700|NFCP/i.test(p))
      if (!printerName && printersData.printers.length > 0) {
        printerName = printersData.printers[0]
      }
      
      const orderData = {
        companyTitle: '怀化市兴隆农业开发有限公司出货单',
        customerName: order.customerName,
        orderNo: order.orderNo,
        date: new Date().toISOString().split('T')[0],
        deliverer: '',
        receiver: '',
        lines: order.items.map(item => ({
          productName: item.productName,
          unit: item.unit,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          remark: ''
        }))
      }
      
      const printResp = await fetch(
        `${PRINT_SERVICE_URL}/api/print/shipping-order?printerName=${encodeURIComponent(printerName)}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(orderData)
        }
      )
      
      if (!printResp.ok) {
        const error = await printResp.json()
        throw new Error(error.message || '打印失败')
      }
      
      const result = await printResp.json()
      setPrintStatus(`${result.message} → ${printerName}`)
      setPrintSuccess(true)
      
    } catch (error) {
      setPrintStatus(error.message)
      setPrintSuccess(false)
    } finally {
      setPrinting(false)
    }
  }
  
  return (
    <div>
      <h2>订单详情</h2>
      <div>订单号：{order.orderNo}</div>
      <div>客户：{order.customerName}</div>
      
      <button onClick={printShippingOrder} disabled={printing}>
        {printing ? '打印中...' : '打印出货单'}
      </button>
      
      {printStatus && (
        <p style={{ color: printSuccess ? 'green' : 'red' }}>
          {printStatus}
        </p>
      )}
    </div>
  )
}
```

---

## 步骤 3：处理 HTTPS 商城访问 HTTP 本地服务

如果商城使用 HTTPS，浏览器会阻止 HTTPS 页面调用 HTTP 的本地服务（混合内容警告）。

### 解决方案 1：为本地服务配置 HTTPS

生成自签名证书：

```powershell
# 使用 Java keytool
cd C:\Users\Rachel\IdeaProjects\shipping-order-print\backend

keytool -genkeypair -alias shipping-print -keyalg RSA -keysize 2048 `
  -storetype PKCS12 -keystore keystore.p12 -validity 3650 `
  -dname "CN=localhost, OU=Print, O=XingLong, L=HuaiHua, ST=HuNan, C=CN"
```

修改 `application.yml`：

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: shipping-print
```

前端调用改为：`https://localhost:8443`

浏览器首次访问会提示证书不受信任，点击"继续前往"即可。

### 解决方案 2：使用 HTTP 商城或在浏览器中允许不安全内容

Chrome 浏览器设置：
1. 访问商城页面
2. 地址栏右侧点击盾牌图标
3. 选择"加载不安全的脚本"

---

## 步骤 4：用户操作流程

1. **首次使用**：
   - 用户在本地 Windows 启动打印服务（双击 start.bat）
   - 验证打印机已连接并开机

2. **日常使用**：
   - 打开商城网站 `http://119.29.98.147:8899`
   - 进入订单详情页
   - 点击"打印出货单"按钮
   - 打印机自动打印出货单

3. **故障处理**：
   - 如果提示"本地打印服务未启动"，检查 Windows 上的打印服务是否运行
   - 如果提示"未找到打印机"，检查打印机是否正确安装

---

## 开机自启动（可选）

### Windows 任务计划程序

创建开机自启动任务：

```powershell
$action = New-ScheduledTaskAction -Execute "java" `
  -Argument "-jar C:\Users\Rachel\IdeaProjects\shipping-order-print\backend\target\shipping-order-print-1.0.0-SNAPSHOT.jar" `
  -WorkingDirectory "C:\Users\Rachel\IdeaProjects\shipping-order-print\backend"

$trigger = New-ScheduledTaskTrigger -AtStartup

$principal = New-ScheduledTaskPrincipal -UserId "Rachel" -LogonType Interactive

Register-ScheduledTask -TaskName "ShippingPrintService" `
  -Action $action -Trigger $trigger -Principal $principal `
  -Description "出货单打印服务"
```

### Windows 服务

或使用 NSSM 将 Java 应用注册为 Windows 服务：

```powershell
# 下载 NSSM: https://nssm.cc/download
nssm install ShippingPrintService "C:\Program Files\Java\jdk-17\bin\java.exe" `
  "-jar C:\Users\Rachel\IdeaProjects\shipping-order-print\backend\target\shipping-order-print-1.0.0-SNAPSHOT.jar"

nssm start ShippingPrintService
```

---

## 常见问题

### Q1: 浏览器提示 CORS 错误

确保后端已添加 `CorsConfig.java` 并重启服务。

### Q2: 无法连接本地服务

检查：
1. 打印服务是否运行：`curl http://localhost:8080/api/printers`
2. 防火墙是否开放端口 8080
3. 浏览器控制台查看具体错误信息

### Q3: HTTPS 商城无法调用 HTTP 本地服务

参考上面的"步骤 3"配置 HTTPS 或允许混合内容。

### Q4: 打印机不可用

1. 检查打印机是否开机并连接 USB
2. 运行队列设置脚本：`.\scripts\setup-dpk700-printer.ps1`
3. 检查 Windows 设备管理器中打印机状态

---

## 下一步

请告诉我商城系统使用的技术栈（纯 HTML / Vue / React / PHP / Java 等），我可以提供更具体的集成代码。

另外，如果你允许我通过 SSH 访问服务器，我可以直接查看商城代码并完成集成。需要的话请运行：

```powershell
# 在本地 Windows 添加临时 Bash 权限让我可以 SSH
# 或者手动执行: ssh root@119.29.98.147 "ls -la /var/www /opt"
```

并告诉我商城项目的路径。
