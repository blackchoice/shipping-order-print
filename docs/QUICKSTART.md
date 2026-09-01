# 快速开始

## 1. 一键构建部署包

在项目根目录运行：

```powershell
.\scripts\build-and-package.ps1
```

这会生成 `deploy/` 目录，包含：
- `shipping-print.jar` - 后端可执行 JAR
- `frontend/` - 前端静态资源
- `application.yml` - 配置文件
- `start.sh` / `start.bat` - 启动脚本
- `nginx-snippet.conf` - Nginx 配置示例

## 2. 部署到服务器

### 方式 A：使用自动部署脚本

```powershell
.\scripts\deploy-to-remote.ps1 -RemoteHost 119.29.98.147 -RemoteUser root -BackendPort 8081
```

### 方式 B：手动部署

**上传文件**

```bash
scp -r deploy/* root@119.29.98.147:/opt/shipping-print/
```

**SSH 登录服务器**

```bash
ssh root@119.29.98.147
cd /opt/shipping-print

# 修改配置
vi application.yml
# 修改 server.port 为 8081（避免与 8899 冲突）

# 启动服务（Linux）
chmod +x start.sh
./start.sh

# 或安装为 systemd 服务
sudo cp shipping-print.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable shipping-print
sudo systemctl start shipping-print
```

## 3. 配置 Nginx

编辑服务器上的 Nginx 配置（通常在 `/etc/nginx/sites-enabled/` 或 `/etc/nginx/conf.d/`）：

```bash
sudo vi /etc/nginx/conf.d/default.conf
```

在监听 8899 端口的 `server` 块中添加：

```nginx
server {
    listen 8899;
    
    # 主网页（保持现有配置）
    location / {
        root /var/www/html;
        index index.html;
    }
    
    # === 添加以下内容 ===
    
    # 打印模块前端
    location /print/ {
        alias /opt/shipping-print/frontend/;
        try_files $uri $uri/ /print/index.html;
    }
    
    # 打印模块 API
    location /api/print/ {
        proxy_pass http://localhost:8081/api/print/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
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

## 4. 验证部署

**检查后端服务**

```bash
curl http://localhost:8081/api/printers
```

**访问前端页面**

浏览器打开：`http://119.29.98.147:8899/print/`

## 5. 集成到主网页

在主网页导航菜单中添加链接：

```html
<nav>
    <a href="/">首页</a>
    <a href="/print/" target="_blank">出货单打印</a>
</nav>
```

或使用 iframe 嵌入：

```html
<iframe src="/print/" width="100%" height="800px" frameborder="0"></iframe>
```

## 故障排查

**后端无法启动**

```bash
# 查看日志
tail -f /opt/shipping-print/app.log

# 检查端口占用
sudo netstat -tlnp | grep 8081

# 检查 Java 版本（需要 Java 17+）
java -version
```

**前端 404**

检查 Nginx 配置中的 `alias` 路径是否正确：

```bash
ls -la /opt/shipping-print/frontend/
# 应该看到 index.html 等文件
```

**API 调用失败**

检查浏览器控制台网络请求，确认：
1. 请求路径正确（`/api/printers`, `/api/print/shipping-order`）
2. 后端服务正常运行
3. Nginx 代理配置正确

## 更多信息

- 详细部署说明：[docs/deployment-guide.md](./deployment-guide.md)
- 集成示例：[docs/integration-examples.md](./integration-examples.md)
- 项目说明：[README.md](./README.md)
