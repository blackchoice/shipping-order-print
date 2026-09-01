# 部署到远程服务器脚本

param(
    [Parameter(Mandatory=$true)]
    [string]$RemoteHost = "119.29.98.147",

    [Parameter(Mandatory=$false)]
    [string]$RemoteUser = "root",

    [Parameter(Mandatory=$false)]
    [int]$BackendPort = 8081,

    [Parameter(Mandatory=$false)]
    [string]$DeployPath = "/opt/shipping-print"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 部署到远程服务器 ===" -ForegroundColor Cyan
Write-Host "目标服务器: $RemoteUser@$RemoteHost" -ForegroundColor Gray
Write-Host "部署路径: $DeployPath" -ForegroundColor Gray
Write-Host "后端端口: $BackendPort" -ForegroundColor Gray

# 检查是否已构建
if (-not (Test-Path ".\deploy\shipping-print.jar")) {
    Write-Host "`n部署包不存在，正在构建..." -ForegroundColor Yellow
    & .\scripts\build-and-package.ps1
}

# 1. 上传文件
Write-Host "`n[1/4] 上传文件到服务器..." -ForegroundColor Yellow

# 检查是否安装了 scp
$scpCommand = Get-Command scp -ErrorAction SilentlyContinue
if (-not $scpCommand) {
    Write-Host "错误: 未找到 scp 命令" -ForegroundColor Red
    Write-Host "请安装 OpenSSH 客户端或使用其他方式上传 deploy 目录" -ForegroundColor Yellow
    Write-Host "`n手动上传步骤:" -ForegroundColor Cyan
    Write-Host "1. 使用 WinSCP/FileZilla 等工具上传 deploy 目录到服务器"
    Write-Host "2. 或使用: scp -r .\deploy $RemoteUser@${RemoteHost}:$DeployPath"
    exit 1
}

# 创建远程目录
ssh "$RemoteUser@$RemoteHost" "mkdir -p $DeployPath"

# 上传部署包
scp -r .\deploy\* "$RemoteUser@${RemoteHost}:$DeployPath/"

if ($LASTEXITCODE -ne 0) {
    Write-Host "文件上传失败" -ForegroundColor Red
    exit 1
}

# 2. 修改配置文件
Write-Host "`n[2/4] 配置后端端口..." -ForegroundColor Yellow

$remoteScript = @"
cd $DeployPath
sed -i 's/port: 8080/port: $BackendPort/' application.yml
chmod +x start.sh
echo "配置已更新"
"@

ssh "$RemoteUser@$RemoteHost" $remoteScript

# 3. 安装 systemd 服务（可选）
Write-Host "`n[3/4] 安装 systemd 服务..." -ForegroundColor Yellow

$installService = @"
cd $DeployPath
# 更新 systemd 服务文件中的路径
sed -i "s|/opt/shipping-print|$DeployPath|g" shipping-print.service
sed -i "s|User=www-data|User=$RemoteUser|g" shipping-print.service

# 安装服务
sudo cp shipping-print.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable shipping-print
sudo systemctl restart shipping-print

echo "服务已安装并启动"
sudo systemctl status shipping-print --no-pager
"@

$response = Read-Host "`n是否安装为 systemd 服务？(y/n)"
if ($response -eq 'y' -or $response -eq 'Y') {
    ssh "$RemoteUser@$RemoteHost" $installService
} else {
    Write-Host "跳过服务安装，可以使用 ./start.sh 手动启动" -ForegroundColor Gray
}

# 4. 配置 Nginx
Write-Host "`n[4/4] Nginx 配置提示" -ForegroundColor Yellow
Write-Host @"

请手动将以下配置添加到 Nginx server 块中：

location /print/ {
    alias $DeployPath/frontend/;
    try_files `$uri `$uri/ /print/index.html;
}

location /api/print/ {
    proxy_pass http://localhost:$BackendPort/api/print/;
    proxy_set_header Host `$host;
    proxy_set_header X-Real-IP `$remote_addr;
}

location /api/printers {
    proxy_pass http://localhost:$BackendPort/api/printers;
    proxy_set_header Host `$host;
    proxy_set_header X-Real-IP `$remote_addr;
}

然后执行：
sudo nginx -t
sudo systemctl reload nginx

"@ -ForegroundColor Cyan

Write-Host "=== 部署完成 ===" -ForegroundColor Green
Write-Host "`n访问地址: http://${RemoteHost}:8899/print/" -ForegroundColor Cyan
Write-Host "后端 API: http://${RemoteHost}:$BackendPort/api/printers" -ForegroundColor Cyan
