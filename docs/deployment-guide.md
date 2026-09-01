# 部署集成指南

## 目标环境

- 服务器：119.29.98.147
- 端口：8899
- 目标：将本打印模块集成到现有网页

## 部署方案

### 方案 A：独立服务 + 前端路由集成（推荐）

后端作为独立服务运行，前端页面嵌入到主网页的路由中。

#### 1. 后端部署

**打包 JAR**

```powershell
cd backend
.\mvnw.cmd clean package -DskipTests
```

生成 `backend/target/shipping-order-print-1.0.0-SNAPSHOT.jar`

**上传到服务器**

```bash
scp backend/target/shipping-order-print-1.0.0-SNAPSHOT.jar user@119.29.98.147:/opt/shipping-print/
```

**修改配置（服务器端）**

创建 `/opt/shipping-print/application.yml`：

```yaml
server:
  port: 8081  # 与主网页 8899 区分

spring:
  application:
    name: shipping-order-print

print:
  printer-name: "NFCP DPK700"
  encoding: GBK
  # ... 其他配置保持不变
```

**启动服务**

```bash
cd /opt/shipping-print
nohup java -jar shipping-order-print-1.0.0-SNAPSHOT.jar \
  --spring.config.location=./application.yml > app.log 2>&1 &
```

或使用 systemd 服务（推荐）：

创建 `/etc/systemd/system/shipping-print.service`：

```ini
[Unit]
Description=Shipping Order Print Service
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/shipping-print
ExecStart=/usr/bin/java -jar /opt/shipping-print/shipping-order-print-1.0.0-SNAPSHOT.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable shipping-print
sudo systemctl start shipping-print
```

#### 2. 前端集成

**方式 2.1：构建并嵌入主网页**

```bash
cd frontend
npm install
npm run build
```

将 `frontend/dist` 目录内容上传到主网页的子路径，例如 `/print`

**方式 2.2：主网页中通过 iframe 嵌入**

在主网页 HTML 中添加：

```html
<iframe src="/print/" width="100%" height="800px" frameborder="0"></iframe>
```

#### 3. Nginx 反向代理配置

在服务器的 Nginx 配置中添加（假设主网页通过 Nginx 提供）：

```nginx
server {
    listen 8899;
    server_name 119.29.98.147;

    # 主网页静态资源
    location / {
        root /var/www/main-site;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 打印模块前端
    location /print/ {
        alias /var/www/shipping-print/;
        try_files $uri $uri/ /print/index.html;
    }

    # 打印模块 API 代理到后端 8081
    location /api/print/ {
        proxy_pass http://localhost:8081/api/print/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/printers {
        proxy_pass http://localhost:8081/api/printers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

重启 Nginx：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

### 方案 B：合并到主项目代码

如果主网页也是 Spring Boot + Vue，可以直接合并代码。

#### 后端合并

1. 复制 Java 包到主项目：
   - `com.xinglong.print.print.*`
   - `com.xinglong.print.config.PrintProperties`
   - `com.xinglong.print.web.*`

2. 合并 `application.yml` 配置（`print.*` 节点）

3. 确保主项目 pom.xml 包含必要依赖：
   - `spring-boot-starter-web`
   - `spring-boot-starter-validation`

#### 前端合并

1. 复制 `frontend/src/*` 到主项目前端对应目录

2. 在主项目路由中添加打印页面：

```javascript
// 主项目 router/index.js
import ShippingPrint from '@/views/ShippingPrint.vue'

const routes = [
  // ... 其他路由
  {
    path: '/print/shipping-order',
    name: 'ShippingPrint',
    component: ShippingPrint
  }
]
```

3. 主导航菜单中添加入口：

```html
<a href="/print/shipping-order">出货单打印</a>
```

---

## 网络与安全配置

### 打印机访问

**重要**：打印功能需要后端服务能访问 Windows 打印队列。

- 如果后端运行在 Windows 服务器上，确保打印机已正确安装并共享
- 如果后端运行在 Linux 服务器上，需要通过以下方式之一：
  1. 通过 SMB/SAMBA 访问 Windows 打印机共享
  2. 后端仅生成 ESC/P 指令，由客户端浏览器下载后本地打印
  3. 部署轻量级 Windows 打印代理服务

### CORS 配置（如有跨域需求）

如果前端和后端不在同一域名/端口，需添加 CORS 配置：

```java
// backend/src/main/java/com/xinglong/print/config/CorsConfig.java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://119.29.98.147:8899")
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowCredentials(true);
            }
        };
    }
}
```

---

## 验证部署

1. 检查后端服务：
   ```bash
   curl http://119.29.98.147:8081/api/printers
   ```

2. 访问前端页面：
   ```
   http://119.29.98.147:8899/print/
   ```

3. 测试打印流程：
   - 选择打印机
   - 填写出货单信息
   - 点击"打印预览"查看版式
   - 点击"打印"发送到打印机

---

## 故障排查

### 后端无法启动

```bash
# 查看日志
tail -f /opt/shipping-print/app.log

# 检查端口占用
netstat -tlnp | grep 8081
```

### 前端 API 调用失败

1. 检查浏览器控制台网络请求
2. 确认 Nginx 代理配置正确
3. 检查后端 CORS 设置

### 打印机不可用

1. 确认打印机在 Windows 设备管理器中正常
2. 运行打印机队列设置脚本：
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\setup-dpk700-printer.ps1
   ```
3. 检查 `application.yml` 中的 `printer-name` 是否匹配实际打印机名称

---

## 下一步

请告诉我：

1. 服务器是 Windows 还是 Linux？
2. 打印机连接在哪台机器上？
3. 主网页使用什么技术栈（纯静态 / PHP / Spring Boot / Node.js 等）？
4. 希望采用哪种集成方案（独立服务 or 代码合并）？

我会根据你的实际情况提供具体的部署脚本。
