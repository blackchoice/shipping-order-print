# 快速执行：克隆 CRMEB 并集成打印功能

## 📦 项目信息

- **商城系统**: CRMEB Java (Spring Boot + Vue)
- **GitHub**: https://github.com/blackchoice/crmeb-java-deploy
- **目标位置**: D:\IdeaProjects\crmeb-java-deploy

## 🚀 立即执行

在 PowerShell 中运行：

```powershell
C:\Users\Rachel\IdeaProjects\shipping-order-print\scripts\clone-crmeb-integrate.ps1
```

## 📋 脚本会自动完成

### ✅ 自动化步骤

1. **克隆 CRMEB 项目**
   - 从 GitHub 下载到 D:\IdeaProjects
   - 检查项目结构

2. **集成后端打印模块**
   - 复制 Java 打印代码到 CRMEB
   - 添加 CORS 配置
   - 生成配置说明

3. **集成前端打印功能**
   - 复制 JavaScript 客户端库
   - 搜索订单详情页面
   - 提供集成代码示例

4. **生成完整集成指南**
   - 详细的分步说明
   - CRMEB 特定的代码示例
   - 部署和测试步骤

### 📝 需要手动完成的步骤

脚本执行后，你需要：

1. **配置后端** - 在 application.yml 添加打印配置
2. **修改订单页面** - 添加打印按钮（有完整代码示例）
3. **测试功能** - 启动服务并测试打印

所有详细说明都会生成在 `D:\IdeaProjects\crmeb-java-deploy\打印功能集成指南.md`

## 🎯 CRMEB 订单页面集成预览

### Vue 组件修改示例

在订单详情页面（如 `order/detail.vue`）添加：

```vue
<template>
  <div class="order-detail">
    <!-- 现有订单信息 -->
    
    <!-- 新增打印按钮 -->
    <el-button 
      type="primary" 
      icon="el-icon-printer"
      @click="handlePrint"
      :loading="printing">
      打印出货单
    </el-button>
  </div>
</template>

<script>
import ShippingPrintClient from '@/utils/shipping-print-client.js'

export default {
  data() {
    return {
      printing: false
    }
  },
  
  methods: {
    async handlePrint() {
      this.printing = true
      
      const printData = {
        orderNo: this.orderDetail.orderId,
        customerName: this.orderDetail.realName,
        date: this.orderDetail.createTime.split(' ')[0],
        items: this.orderDetail.orderInfoList.map(item => ({
          productName: item.productName,
          unit: item.unit || '件',
          quantity: item.payNum,
          unitPrice: item.price
        }))
      }
      
      const result = await ShippingPrintClient.printOrder(printData)
      this.$message[result.success ? 'success' : 'error'](result.message)
      
      this.printing = false
    }
  }
}
</script>
```

## 📁 项目结构预览

```
D:\IdeaProjects\crmeb-java-deploy\
├── crmeb\                          # 后端 Spring Boot
│   ├── crmeb-service\
│   │   └── src\main\java\com\zbkj\service\
│   │       └── print\              # ✨ 打印模块（自动添加）
│   └── crmeb-admin\
│       └── src\main\java\com\zbkj\admin\
│           └── config\
│               └── CorsConfig.java # ✨ CORS 配置（自动添加）
├── admin\                          # 前端 Vue
│   └── src\
│       ├── utils\
│       │   └── shipping-print-client.js  # ✨ 客户端库（自动添加）
│       └── views\
│           └── order\              # 订单页面（需手动修改）
└── 打印功能集成指南.md              # ✨ 完整指南（自动生成）
```

## ⚡ 快速测试流程

### 1. 启动本地打印服务
```powershell
C:\Users\Rachel\IdeaProjects\shipping-order-print\scripts\start-print-service.bat
```

### 2. 启动 CRMEB
```powershell
# 后端
cd D:\IdeaProjects\crmeb-java-deploy\crmeb
mvn spring-boot:run

# 前端
cd D:\IdeaProjects\crmeb-java-deploy\admin
npm run serve
```

### 3. 测试打印
- 访问 CRMEB 后台
- 进入订单详情页
- 点击"打印出货单"

## 🔧 常见问题

### Q: CRMEB 项目启动失败？
A: CRMEB 需要 MySQL、Redis 等依赖，参考项目 README 配置

### Q: 找不到订单详情页面？
A: 通常在 `admin/src/views/order/` 目录下，脚本会自动搜索

### Q: 打印数据字段不匹配？
A: 集成指南中有字段映射说明，根据 CRMEB 实际字段调整

## 📞 获取帮助

如果遇到问题：

1. 查看生成的集成指南：`D:\IdeaProjects\crmeb-java-deploy\打印功能集成指南.md`
2. 参考完整示例：`C:\Users\Rachel\IdeaProjects\shipping-order-print\docs\商城集成-完整示例.html`
3. 告诉我具体遇到的问题

---

## 🎬 现在开始！

```powershell
# 复制这行命令执行
C:\Users\Rachel\IdeaProjects\shipping-order-print\scripts\clone-crmeb-integrate.ps1
```

执行后告诉我进展，我会继续帮你完成具体的页面集成！
