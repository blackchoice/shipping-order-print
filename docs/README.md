# 出货单打印模块 - 集成完整包

本目录包含将打印模块集成到商城系统所需的全部文件。

## 📁 文件清单

### 核心文件
- **商城集成-JavaScript客户端.js** - 浏览器端打印客户端库
- **商城集成-完整示例.html** - 完整的集成示例页面
- **商城集成-实施步骤.md** - 详细的集成步骤文档

### 部署文档
- **deployment-guide.md** - 完整部署指南（独立服务 + 代码合并）
- **browser-local-print-integration.md** - 浏览器调用本地打印详细方案
- **integration-examples.md** - 5种技术栈的集成示例
- **QUICKSTART.md** - 快速开始指南

## 🚀 快速开始

### 1. 启动本地打印服务

在 Windows 本地机器上：

```powershell
# 双击运行
.\scripts\start-print-service.bat

# 或使用 PowerShell
cd backend
.\mvnw.cmd spring-boot:run
```

验证：访问 http://localhost:8080/api/printers

### 2. 测试打印服务

```powershell
.\scripts\test-print-service.ps1
```

### 3. 集成到商城

#### 方式 A：上传 JavaScript 客户端（推荐）

```bash
# 上传到商城服务器
scp docs/商城集成-JavaScript客户端.js root@119.29.98.147:/var/www/html/js/
```

在商城订单页面引入：

```html
<script src="/js/商城集成-JavaScript客户端.js"></script>

<button onclick="printOrder()">打印出货单</button>

<script>
async function printOrder() {
  const orderData = {
    orderNo: '订单号',
    customerName: '客户名称',
    date: '2026-07-01',
    items: [
      { productName: '商品', unit: '件', quantity: 1, unitPrice: 10 }
    ]
  };
  
  const result = await ShippingPrintClient.printOrder(orderData);
  alert(result.success ? '打印成功' : result.message);
}
</script>
```

#### 方式 B：查看完整示例

在浏览器中打开 `docs/商城集成-完整示例.html` 查看完整的集成示例。

## 📖 架构说明

```
用户浏览器
    ↓ 访问
商城服务器 (119.29.98.147:8899)
    - 提供订单页面和数据

用户浏览器（同一个窗口）
    ↓ JavaScript 调用
本地打印服务 (localhost:8080)
    - 运行在用户 Windows 机器上
    ↓ 直接打印
本地打印机 (NFCP DPK700)
```

**关键优势**：
- 浏览器可以同时访问远程商城和本地打印服务
- 不需要服务器和本地机器互相访问
- 不需要安装浏览器插件
- 打印数据不经过外部服务器

## 🔧 技术栈支持

- ✅ 纯 HTML/JavaScript
- ✅ Vue.js 2/3
- ✅ React
- ✅ jQuery
- ✅ Angular
- ✅ Spring Boot + Thymeleaf
- ✅ PHP
- ✅ ASP.NET

详见 `integration-examples.md` 各技术栈的具体示例。

## 📝 集成步骤概览

1. **后端准备**：
   - 添加 CORS 配置（已完成）
   - 启动本地打印服务
   - 配置防火墙开放 8080 端口

2. **前端集成**：
   - 上传 JavaScript 客户端库到商城服务器
   - 在订单页面引入脚本
   - 添加打印按钮和调用代码

3. **测试验证**：
   - 检查打印服务状态
   - 测试打印功能
   - 验证 CORS 配置

详细步骤见 `商城集成-实施步骤.md`

## 🛠️ API 参考

### ShippingPrintClient.printOrder(orderData)

打印出货单

**参数**：
```javascript
{
  orderNo: string,          // 订单号
  customerName: string,     // 客户名称
  date: string,            // 日期 YYYY-MM-DD
  deliverer: string,       // 送货人（可选）
  receiver: string,        // 收货人（可选）
  items: [                 // 商品明细
    {
      productName: string, // 商品名称
      unit: string,       // 单位
      quantity: number,   // 数量
      unitPrice: number,  // 单价
      remark: string      // 备注（可选）
    }
  ]
}
```

**返回**：
```javascript
{
  success: boolean,        // 是否成功
  message: string,         // 消息
  printerName: string     // 使用的打印机名称（成功时）
}
```

### ShippingPrintClient.checkService()

检查打印服务是否在线

**返回**：`Promise<boolean>`

### ShippingPrintClient.listPrinters()

获取可用打印机列表

**返回**：`Promise<string[]>`

### ShippingPrintClient.previewLayout(orderData)

预览打印布局（不实际打印）

**返回**：
```javascript
{
  lines: string[],         // 每行内容
  pageWidthChars: number,  // 页面宽度（字符数）
  totalLines: number       // 总行数
}
```

## ⚙️ 配置

编辑 `商城集成-JavaScript客户端.js` 中的配置：

```javascript
const CONFIG = {
  // 本地打印服务地址
  printServiceUrl: 'http://localhost:8080',
  
  // 公司抬头
  companyTitle: '怀化市兴隆农业开发有限公司出货单',
  
  // 首选打印机关键词
  preferredPrinterKeywords: ['DPK700', 'NFCP'],
  
  // 调试模式
  debug: true
};
```

## 🐛 故障排查

### 问题：打印服务未启动

**解决**：运行 `.\scripts\start-print-service.bat`

### 问题：CORS 跨域错误

**解决**：检查 `backend/src/main/java/com/xinglong/print/config/CorsConfig.java` 中是否包含商城地址

### 问题：未找到打印机

**解决**：
```powershell
# 运行打印机设置脚本
.\scripts\setup-dpk700-printer.ps1

# 检查打印机
Get-Printer | Select-Object Name
```

### 问题：HTTPS 商城无法调用 HTTP 本地服务

**解决**：参考 `browser-local-print-integration.md` 配置 HTTPS

## 📞 获取帮助

如需进一步协助集成，请提供：

1. 商城系统技术栈（PHP/Java/Node.js/纯静态等）
2. 订单页面代码示例
3. 是否有 SSH 访问权限

我可以直接生成适配的集成代码或远程协助部署。

## 📄 许可证

本模块基于 Spring Boot 和 Vue.js 构建，遵循相应的开源许可证。
