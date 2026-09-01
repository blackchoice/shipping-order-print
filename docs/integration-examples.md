# 集成示例

## 场景 1：主网页是纯静态 HTML

如果你的主网页（8899 端口）是纯静态页面，最简单的方式是通过菜单链接或 iframe 嵌入。

### 方式 A：新窗口打开

在主页面导航菜单中添加：

```html
<nav>
  <a href="/">首页</a>
  <a href="/about.html">关于</a>
  <a href="/print/" target="_blank">出货单打印</a>
</nav>
```

### 方式 B：iframe 嵌入

在主页面中嵌入打印模块：

```html
<!DOCTYPE html>
<html>
<head>
  <title>业务管理系统</title>
  <style>
    .container { display: flex; height: 100vh; }
    .sidebar { width: 200px; background: #2B1D14; }
    .content { flex: 1; }
    iframe { width: 100%; height: 100%; border: none; }
  </style>
</head>
<body>
  <div class="container">
    <nav class="sidebar">
      <a href="#" onclick="loadPage('dashboard')">仪表盘</a>
      <a href="#" onclick="loadPage('orders')">订单管理</a>
      <a href="#" onclick="loadPage('print')">出货单打印</a>
    </nav>
    <div class="content">
      <iframe id="mainFrame" src="/dashboard.html"></iframe>
    </div>
  </div>
  
  <script>
    function loadPage(page) {
      const frame = document.getElementById('mainFrame');
      const routes = {
        dashboard: '/dashboard.html',
        orders: '/orders.html',
        print: '/print/'
      };
      frame.src = routes[page] || '/';
    }
  </script>
</body>
</html>
```

---

## 场景 2：主网页是 Vue.js SPA

如果主网页也是 Vue 3 项目，可以直接集成打印组件。

### 步骤 1：复制组件

将以下文件复制到主项目：

```
主项目/src/
├── components/
│   └── print/
│       ├── ShippingPrintForm.vue  (App.vue 重命名)
│       └── PrintPreview.vue
├── api/
│   └── print.js  (api.js)
└── views/
    └── ShippingPrint.vue  (新建路由页面)
```

### 步骤 2：创建路由页面

`src/views/ShippingPrint.vue`:

```vue
<script setup>
import ShippingPrintForm from '@/components/print/ShippingPrintForm.vue'
</script>

<template>
  <div class="page-container">
    <ShippingPrintForm />
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
  background: #F5EFE6;
  min-height: 100vh;
}
</style>
```

### 步骤 3：配置路由

`src/router/index.js`:

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import ShippingPrint from '@/views/ShippingPrint.vue'

const routes = [
  // ... 现有路由
  {
    path: '/print/shipping-order',
    name: 'ShippingPrint',
    component: ShippingPrint,
    meta: { title: '出货单打印' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

### 步骤 4：添加导航入口

主导航组件中：

```vue
<template>
  <nav>
    <router-link to="/">首页</router-link>
    <router-link to="/orders">订单</router-link>
    <router-link to="/print/shipping-order">打印</router-link>
  </nav>
</template>
```

### 步骤 5：配置 API 代理

`vite.config.js`:

```javascript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',  // 打印后端端口
        changeOrigin: true
      }
    }
  }
})
```

---

## 场景 3：主网页是 React

如果主网页是 React 项目，需要将 Vue 组件转换为 React 组件，或通过 Web Components 封装。

### 方式 A：转换为 React 组件（推荐）

关键改动：

```jsx
// src/components/ShippingPrint.jsx
import { useState, useEffect, useMemo } from 'react'
import { listPrinters, previewLayout, printOrder, downloadEscp } from '../api/print'
import PrintPreview from './PrintPreview'

export default function ShippingPrint() {
  const [form, setForm] = useState({
    companyTitle: '怀化市兴隆农业开发有限公司出货单',
    customerName: '本部食堂',
    orderNo: 'XS-202607010206',
    date: '2026-07-01',
    deliverer: '',
    receiver: '',
    lines: [
      { productName: '牛腩', unit: '公斤', quantity: 4, unitPrice: 70, remark: '' }
    ]
  })
  
  const [printers, setPrinters] = useState([])
  const [selectedPrinter, setSelectedPrinter] = useState('')
  const [layout, setLayout] = useState(null)
  
  const total = useMemo(() => {
    return form.lines.reduce((sum, row) => {
      return sum + (row.quantity || 0) * (row.unitPrice || 0)
    }, 0)
  }, [form.lines])
  
  const updateLine = (index, field, value) => {
    const newLines = [...form.lines]
    newLines[index][field] = value
    setForm({ ...form, lines: newLines })
  }
  
  const handlePrint = async () => {
    if (!selectedPrinter) {
      alert('请选择打印机')
      return
    }
    try {
      await printOrder(form, selectedPrinter)
      alert('打印成功')
    } catch (e) {
      alert(e.message)
    }
  }
  
  useEffect(() => {
    listPrinters().then(data => setPrinters(data.printers || []))
  }, [])
  
  useEffect(() => {
    const timer = setTimeout(() => {
      previewLayout(form).then(setLayout)
    }, 250)
    return () => clearTimeout(timer)
  }, [form])
  
  return (
    <div className="shipping-print">
      {/* 表单和预览 UI */}
    </div>
  )
}
```

### 方式 B：作为微前端模块

使用 qiankun / micro-app 将打印模块作为子应用加载：

```javascript
// 主应用 main.js
import { registerMicroApps, start } from 'qiankun'

registerMicroApps([
  {
    name: 'shipping-print',
    entry: 'http://localhost:5173',  // 打印模块独立运行
    container: '#print-container',
    activeRule: '/print'
  }
])

start()
```

---

## 场景 4：主网页是 Spring Boot Thymeleaf

如果主网页使用 Thymeleaf 模板引擎，可以将打印模块作为一个新页面集成。

### 步骤 1：合并后端代码

将本项目的 Java 包复制到主项目：

```
主项目/src/main/java/com/yourcompany/
├── config/
│   └── PrintProperties.java
├── print/
│   ├── escp/
│   │   └── EscpCommandWriter.java
│   ├── AmountToChinese.java
│   ├── RawPrintService.java
│   └── ShippingOrderEscpBuilder.java
└── web/
    ├── dto/
    │   ├── ShippingOrderPrintRequest.java
    │   └── ShippingOrderLayout.java
    └── PrintController.java
```

### 步骤 2：创建 Thymeleaf 模板

`src/main/resources/templates/print/shipping-order.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <title>出货单打印</title>
  <link rel="stylesheet" th:href="@{/css/print.css}">
</head>
<body>
  <div class="container">
    <h1>出货单打印</h1>
    
    <form id="printForm">
      <label>
        标题
        <input name="companyTitle" value="怀化市兴隆农业开发有限公司出货单">
      </label>
      
      <!-- 其他表单字段 -->
      
      <button type="button" onclick="doPrint()">打印</button>
    </form>
    
    <div id="preview"></div>
  </div>
  
  <script th:src="@{/js/print.js}"></script>
</body>
</html>
```

`src/main/resources/static/js/print.js`:

```javascript
async function doPrint() {
  const form = document.getElementById('printForm')
  const data = new FormData(form)
  const payload = Object.fromEntries(data)
  
  const response = await fetch('/api/print/shipping-order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  
  if (response.ok) {
    alert('打印成功')
  } else {
    alert('打印失败')
  }
}
```

### 步骤 3：添加控制器路由

```java
@Controller
public class PrintPageController {
    @GetMapping("/print/shipping-order")
    public String shippingOrderPage() {
        return "print/shipping-order";
    }
}
```

---

## 场景 5：作为独立 Web Service 供其他系统调用

如果其他业务系统需要调用打印功能，可以提供 REST API。

### API 文档

**列出打印机**

```
GET /api/printers
Response: { "printers": ["NFCP DPK700", "Microsoft Print to PDF"] }
```

**打印出货单**

```
POST /api/print/shipping-order?printerName=NFCP%20DPK700
Content-Type: application/json

{
  "companyTitle": "怀化市兴隆农业开发有限公司出货单",
  "customerName": "本部食堂",
  "orderNo": "XS-202607010206",
  "date": "2026-07-01",
  "deliverer": "",
  "receiver": "周高玉",
  "lines": [
    {
      "productName": "牛腩",
      "unit": "公斤",
      "quantity": 4,
      "unitPrice": 70.00,
      "remark": ""
    }
  ]
}

Response: { "message": "打印成功", "printerName": "NFCP DPK700" }
```

**预览版式**

```
POST /api/print/shipping-order/layout
(同上 body)

Response: {
  "lines": ["怀化市兴隆农业开发有限公司出货单", "..."],
  "pageWidthChars": 100,
  "totalLines": 28
}
```

### 调用示例（Python）

```python
import requests

def print_shipping_order(order_data, printer_name="NFCP DPK700"):
    url = "http://119.29.98.147:8081/api/print/shipping-order"
    params = {"printerName": printer_name}
    
    response = requests.post(url, json=order_data, params=params)
    response.raise_for_status()
    
    return response.json()

# 使用
order = {
    "companyTitle": "怀化市兴隆农业开发有限公司出货单",
    "customerName": "本部食堂",
    "orderNo": "XS-202607010206",
    "date": "2026-07-01",
    "lines": [
        {"productName": "牛腩", "unit": "公斤", "quantity": 4, "unitPrice": 70}
    ]
}

result = print_shipping_order(order)
print(result['message'])
```

---

## 选择建议

| 主网页技术栈 | 推荐方案 | 工作量 |
|------------|---------|-------|
| 纯静态 HTML | iframe 嵌入 | 极低 |
| Vue 3 SPA | 组件直接集成 | 低 |
| React SPA | 转换为 React 组件 | 中 |
| Spring Boot + Thymeleaf | 后端合并 + 新页面 | 中 |
| 其他独立系统 | REST API 调用 | 低 |

如需具体技术栈的详细集成代码，请告知你的主网页使用的技术。
