# 构建并打包完整部署包

param(
    [string]$OutputDir = ".\deploy"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 出货单打印模块 - 构建部署包 ===" -ForegroundColor Cyan

# 1. 构建后端
Write-Host "`n[1/3] 构建后端 JAR..." -ForegroundColor Yellow
Push-Location backend
try {
    & .\mvnw.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }
} finally {
    Pop-Location
}

# 2. 构建前端
Write-Host "`n[2/3] 构建前端静态资源..." -ForegroundColor Yellow
Push-Location frontend
try {
    if (-not (Test-Path "node_modules")) {
        Write-Host "安装依赖..." -ForegroundColor Gray
        npm install
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
} finally {
    Pop-Location
}

# 3. 打包部署文件
Write-Host "`n[3/3] 打包部署文件..." -ForegroundColor Yellow

if (Test-Path $OutputDir) {
    Remove-Item $OutputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

# 后端 JAR
Copy-Item "backend\target\shipping-order-print-1.0.0-SNAPSHOT.jar" "$OutputDir\shipping-print.jar"

# 前端静态资源
Copy-Item "frontend\dist" "$OutputDir\frontend" -Recurse

# 配置文件模板
Copy-Item "backend\src\main\resources\application.yml" "$OutputDir\application.yml"

# 文档
Copy-Item "README.md" "$OutputDir\"
Copy-Item "docs\deployment-guide.md" "$OutputDir\"

# 生成启动脚本
@"
#!/bin/bash
# 启动脚本（Linux）

cd `$(dirname `$0)
nohup java -jar shipping-print.jar --spring.config.location=./application.yml > app.log 2>&1 &
echo `$! > app.pid
echo "服务已启动，PID: `$(cat app.pid)"
echo "日志: tail -f app.log"
"@ | Out-File "$OutputDir\start.sh" -Encoding UTF8

@"
@echo off
REM 启动脚本（Windows）

cd /d %~dp0
start /b javaw -jar shipping-print.jar --spring.config.location=application.yml
echo 服务已启动
pause
"@ | Out-File "$OutputDir\start.bat" -Encoding ASCII

# 生成 systemd 服务文件模板
@"
[Unit]
Description=Shipping Order Print Service
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/shipping-print
ExecStart=/usr/bin/java -jar /opt/shipping-print/shipping-print.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
"@ | Out-File "$OutputDir\shipping-print.service" -Encoding UTF8

# Nginx 配置示例
@"
# 将此配置段加入现有 Nginx server 块中

# 打印模块前端
location /print/ {
    alias /var/www/shipping-print/frontend/;
    try_files `$uri `$uri/ /print/index.html;
}

# 打印模块 API 代理
location /api/print/ {
    proxy_pass http://localhost:8081/api/print/;
    proxy_set_header Host `$host;
    proxy_set_header X-Real-IP `$remote_addr;
    proxy_set_header X-Forwarded-For `$proxy_add_x_forwarded_for;
}

location /api/printers {
    proxy_pass http://localhost:8081/api/printers;
    proxy_set_header Host `$host;
    proxy_set_header X-Real-IP `$remote_addr;
}
"@ | Out-File "$OutputDir\nginx-snippet.conf" -Encoding UTF8

Write-Host "`n=== 构建完成 ===" -ForegroundColor Green
Write-Host "部署包位置: $OutputDir" -ForegroundColor Cyan
Write-Host "`n部署步骤:" -ForegroundColor Yellow
Write-Host "1. 将 $OutputDir 目录上传到服务器 /opt/shipping-print/"
Write-Host "2. 修改 application.yml 中的配置（端口、打印机名称等）"
Write-Host "3. Linux: chmod +x start.sh && ./start.sh"
Write-Host "   Windows: 双击 start.bat"
Write-Host "4. 配置 Nginx（参考 nginx-snippet.conf）"
Write-Host "`n详细说明见: deployment-guide.md"
