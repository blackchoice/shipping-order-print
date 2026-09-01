# 从服务器克隆商城项目到本地，集成打印功能后部署回去

param(
    [string]$RemoteHost = "119.29.98.147",
    [string]$RemoteUser = "root",
    [string]$LocalWorkDir = "C:\Users\Rachel\mall-project-clone"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 商城项目克隆与集成脚本 ===" -ForegroundColor Cyan
Write-Host ""

# 步骤 1：查找服务器上的项目位置
Write-Host "[步骤 1/6] 查找服务器项目位置..." -ForegroundColor Yellow
Write-Host ""

Write-Host "正在检查常见目录..." -ForegroundColor Gray
$findProjectCmd = @"
echo '=== /var/www 目录 ==='
ls -la /var/www 2>/dev/null || echo '目录不存在'
echo ''
echo '=== /opt 目录 ==='
ls -la /opt 2>/dev/null || echo '目录不存在'
echo ''
echo '=== /home 目录 ==='
ls -la /home 2>/dev/null || echo '目录不存在'
echo ''
echo '=== 查找端口 8899 的进程 ==='
netstat -tlnp 2>/dev/null | grep 8899 || ss -tlnp 2>/dev/null | grep 8899 || echo '未找到监听 8899 的进程'
echo ''
echo '=== 查找 Nginx 配置 ==='
grep -r '8899' /etc/nginx 2>/dev/null | head -5 || echo 'Nginx 配置中未找到 8899'
echo ''
echo '=== 运行的服务 ==='
ps aux | grep -E 'java|node|php|nginx|python' | grep -v grep | head -10
"@

Write-Host "执行以下命令查找项目：" -ForegroundColor Cyan
Write-Host "ssh $RemoteUser@$RemoteHost" -ForegroundColor Gray
Write-Host ""

$projectInfo = ssh "$RemoteUser@$RemoteHost" $findProjectCmd

Write-Host $projectInfo
Write-Host ""

# 让用户确认项目路径
Write-Host "请根据以上信息，输入商城项目在服务器上的完整路径" -ForegroundColor Yellow
Write-Host "示例: /var/www/mall 或 /opt/shop 或 /home/www/store" -ForegroundColor Gray
$projectPath = Read-Host "项目路径"

if ([string]::IsNullOrWhiteSpace($projectPath)) {
    Write-Host "错误: 项目路径不能为空" -ForegroundColor Red
    exit 1
}

# 步骤 2：创建本地工作目录
Write-Host ""
Write-Host "[步骤 2/6] 创建本地工作目录..." -ForegroundColor Yellow

if (Test-Path $LocalWorkDir) {
    Write-Host "清理现有目录..." -ForegroundColor Gray
    Remove-Item $LocalWorkDir -Recurse -Force
}

New-Item -ItemType Directory -Path $LocalWorkDir -Force | Out-Null
Write-Host "✓ 创建目录: $LocalWorkDir" -ForegroundColor Green

# 步骤 3：从服务器下载项目
Write-Host ""
Write-Host "[步骤 3/6] 从服务器下载项目..." -ForegroundColor Yellow

$downloadDest = Join-Path $LocalWorkDir "mall-original"
Write-Host "下载到: $downloadDest" -ForegroundColor Gray

# 使用 scp 递归下载
scp -r "$RemoteUser@${RemoteHost}:$projectPath" $downloadDest

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ 下载失败" -ForegroundColor Red
    Write-Host "请检查:" -ForegroundColor Yellow
    Write-Host "1. 项目路径是否正确" -ForegroundColor Gray
    Write-Host "2. 是否有读取权限" -ForegroundColor Gray
    Write-Host "3. SSH 连接是否正常" -ForegroundColor Gray
    exit 1
}

Write-Host "✓ 项目下载完成" -ForegroundColor Green

# 步骤 4：分析项目结构
Write-Host ""
Write-Host "[步骤 4/6] 分析项目结构..." -ForegroundColor Yellow

$projectFiles = Get-ChildItem -Path $downloadDest -Recurse -File | Select-Object -First 30
Write-Host "项目文件预览:" -ForegroundColor Gray
$projectFiles | ForEach-Object { Write-Host "  $($_.FullName.Replace($downloadDest, ''))" -ForegroundColor Gray }

# 检测技术栈
$techStack = "未知"
if (Test-Path "$downloadDest/package.json") {
    $techStack = "Node.js / JavaScript"
    Write-Host "✓ 检测到 Node.js 项目" -ForegroundColor Green
}
elseif (Test-Path "$downloadDest/pom.xml") {
    $techStack = "Java / Spring Boot"
    Write-Host "✓ 检测到 Java 项目" -ForegroundColor Green
}
elseif (Test-Path "$downloadDest/composer.json") {
    $techStack = "PHP"
    Write-Host "✓ 检测到 PHP 项目" -ForegroundColor Green
}
elseif (Test-Path "$downloadDest/index.html") {
    $techStack = "静态 HTML"
    Write-Host "✓ 检测到静态 HTML 项目" -ForegroundColor Green
}
elseif (Test-Path "$downloadDest/*.php") {
    $techStack = "PHP"
    Write-Host "✓ 检测到 PHP 项目" -ForegroundColor Green
}

Write-Host "技术栈: $techStack" -ForegroundColor Cyan

# 步骤 5：集成打印功能
Write-Host ""
Write-Host "[步骤 5/6] 集成打印功能..." -ForegroundColor Yellow

# 创建集成目录
$integratedDir = Join-Path $LocalWorkDir "mall-integrated"
Copy-Item -Path $downloadDest -Destination $integratedDir -Recurse -Force
Write-Host "✓ 创建集成副本" -ForegroundColor Green

# 复制打印客户端库
$jsClientSource = "C:\Users\Rachel\IdeaProjects\shipping-order-print\docs\商城集成-JavaScript客户端.js"
$jsClientDest = Join-Path $integratedDir "static\js\shipping-print-client.js"

# 尝试找到静态资源目录
$possibleStaticDirs = @(
    "static/js",
    "public/js",
    "assets/js",
    "js",
    "resources/static/js",
    "src/main/resources/static/js"
)

$staticDir = $null
foreach ($dir in $possibleStaticDirs) {
    $testPath = Join-Path $integratedDir $dir
    if (Test-Path (Split-Path $testPath -Parent)) {
        $staticDir = $testPath
        break
    }
}

if (-not $staticDir) {
    # 如果都不存在，创建默认目录
    $staticDir = Join-Path $integratedDir "js"
}

New-Item -ItemType Directory -Path $staticDir -Force | Out-Null
Copy-Item $jsClientSource -Destination (Join-Path $staticDir "shipping-print-client.js") -Force

Write-Host "✓ 已复制打印客户端库到: $staticDir" -ForegroundColor Green

# 创建集成说明文件
$integrationGuide = @"
# 商城打印功能集成说明

## 已添加的文件

1. JavaScript 客户端库: /js/shipping-print-client.js (或 /static/js/ 或 /public/js/)

## 集成步骤

### 在订单详情页面添加以下代码：

1. 引入打印客户端（在 <head> 或 <body> 底部）：

```html
<script src="/js/shipping-print-client.js"></script>
```

2. 添加打印按钮（在订单详情区域）：

```html
<button onclick="printShippingOrder()" class="btn btn-primary">
    打印出货单
</button>
<div id="printStatus" style="margin-top: 10px;"></div>
```

3. 添加打印函数（在页面的 <script> 标签中）：

```javascript
async function printShippingOrder() {
    const statusEl = document.getElementById('printStatus');

    // 从页面提取订单数据（根据实际页面结构调整）
    const orderData = {
        orderNo: document.getElementById('orderNo').textContent,  // 订单号
        customerName: document.getElementById('customerName').textContent,  // 客户名称
        date: document.getElementById('orderDate').textContent,  // 订单日期
        deliverer: '',  // 送货人（可选）
        receiver: '',   // 收货人（可选）
        items: []  // 商品明细数组
    };

    // 提取商品明细（根据实际表格结构调整）
    const rows = document.querySelectorAll('.order-items-table tbody tr');
    rows.forEach(row => {
        const cells = row.querySelectorAll('td');
        orderData.items.push({
            productName: cells[1].textContent,  // 商品名称
            unit: cells[2].textContent,         // 单位
            quantity: parseFloat(cells[3].textContent) || 0,  // 数量
            unitPrice: parseFloat(cells[4].textContent) || 0, // 单价
            remark: ''
        });
    });

    // 调用打印
    statusEl.textContent = '打印中...';
    statusEl.style.color = 'blue';

    const result = await ShippingPrintClient.printOrder(orderData);

    statusEl.textContent = result.success
        ? '✓ ' + result.message + ' → ' + result.printerName
        : '✗ ' + result.message;
    statusEl.style.color = result.success ? 'green' : 'red';
}
```

## 具体文件位置

根据你的项目结构，需要修改的文件可能是：

- **静态 HTML 项目**: 找到订单详情页面 HTML 文件（如 order-detail.html）
- **PHP 项目**: 找到订单详情页面模板（如 order-detail.php 或 order/detail.php）
- **Java Spring Boot**: 找到 Thymeleaf 模板（如 templates/order/detail.html）
- **Vue/React**: 找到订单详情组件（如 OrderDetail.vue 或 OrderDetail.jsx）

## 注意事项

1. **本地打印服务必须运行**: 用户在本地 Windows 机器上运行打印服务
   ```
   C:\Users\Rachel\IdeaProjects\shipping-order-print\scripts\start-print-service.bat
   ```

2. **数据提取**: 上面示例中的选择器（getElementById、querySelectorAll）需要根据实际页面结构调整

3. **CORS**: 打印服务已配置允许 http://119.29.98.147:8899 跨域访问

## 测试

部署后访问订单详情页，点击"打印出货单"按钮测试。

如有问题，查看浏览器控制台的错误信息。
"@

$integrationGuide | Out-File -FilePath (Join-Path $LocalWorkDir "集成说明.txt") -Encoding UTF8
Write-Host "✓ 已生成集成说明文件" -ForegroundColor Green

# 创建查找订单页面的脚本
$findOrderPageScript = @"
# 查找订单详情页面

# 搜索包含 "订单" 或 "order" 的文件
Write-Host "搜索订单相关页面..." -ForegroundColor Cyan
Get-ChildItem -Path "$integratedDir" -Recurse -File |
    Where-Object { `$_.Name -match '(order|订单)' -and `$_.Extension -match '\.(html|php|vue|jsx|jsp)' } |
    ForEach-Object { Write-Host `$_.FullName }

Write-Host ""
Write-Host "搜索包含'订单详情'关键字的文件..." -ForegroundColor Cyan
Get-ChildItem -Path "$integratedDir" -Recurse -File -Include *.html,*.php,*.vue,*.jsx,*.jsp |
    Where-Object { (Get-Content `$_.FullName -Raw -ErrorAction SilentlyContinue) -match '订单详情|order.*detail' } |
    ForEach-Object { Write-Host `$_.FullName }
"@

$findOrderPageScript | Out-File -FilePath (Join-Path $LocalWorkDir "查找订单页面.ps1") -Encoding UTF8

Write-Host ""
Write-Host "正在搜索订单页面..." -ForegroundColor Cyan
& (Join-Path $LocalWorkDir "查找订单页面.ps1")

Write-Host ""
Write-Host "✓ 打印功能基础文件已集成" -ForegroundColor Green

# 步骤 6：等待用户修改
Write-Host ""
Write-Host "[步骤 6/6] 等待手动修改..." -ForegroundColor Yellow
Write-Host ""
Write-Host "请按以下步骤操作：" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 打开集成目录：$integratedDir" -ForegroundColor White
Write-Host "2. 根据上面搜索结果，找到订单详情页面文件" -ForegroundColor White
Write-Host "3. 参考 '$LocalWorkDir\集成说明.txt' 添加打印功能" -ForegroundColor White
Write-Host "4. 参考 'C:\Users\Rachel\IdeaProjects\shipping-order-print\docs\商城集成-完整示例.html' 查看完整示例" -ForegroundColor White
Write-Host ""
Write-Host "修改完成后，按任意键继续部署..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# 步骤 7：部署回服务器
Write-Host ""
Write-Host "[步骤 7/6] 部署回服务器..." -ForegroundColor Yellow

# 备份服务器原项目
$backupPath = "$projectPath.backup.$(Get-Date -Format 'yyyyMMdd_HHmmss')"
Write-Host "备份原项目到: $backupPath" -ForegroundColor Gray
ssh "$RemoteUser@$RemoteHost" "cp -r $projectPath $backupPath"

Write-Host "上传修改后的项目..." -ForegroundColor Gray
scp -r "$integratedDir/*" "$RemoteUser@${RemoteHost}:$projectPath/"

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ 上传失败" -ForegroundColor Red
    Write-Host "原项目已备份到: $backupPath" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ 上传完成" -ForegroundColor Green

# 重启服务（根据技术栈）
Write-Host ""
Write-Host "是否需要重启服务？(y/n)" -ForegroundColor Yellow
$restart = Read-Host

if ($restart -eq 'y' -or $restart -eq 'Y') {
    Write-Host "请选择重启方式:" -ForegroundColor Cyan
    Write-Host "1. Nginx 重载配置" -ForegroundColor Gray
    Write-Host "2. 重启 systemd 服务" -ForegroundColor Gray
    Write-Host "3. 手动重启" -ForegroundColor Gray
    $choice = Read-Host "选择 (1-3)"

    switch ($choice) {
        "1" {
            ssh "$RemoteUser@$RemoteHost" "nginx -t && systemctl reload nginx"
            Write-Host "✓ Nginx 已重载" -ForegroundColor Green
        }
        "2" {
            $serviceName = Read-Host "输入服务名称"
            ssh "$RemoteUser@$RemoteHost" "systemctl restart $serviceName"
            Write-Host "✓ 服务已重启" -ForegroundColor Green
        }
        "3" {
            Write-Host "请手动重启服务" -ForegroundColor Yellow
        }
    }
}

# 完成
Write-Host ""
Write-Host "=== 集成完成 ===" -ForegroundColor Green
Write-Host ""
Write-Host "部署信息：" -ForegroundColor Cyan
Write-Host "  原项目备份: $backupPath" -ForegroundColor Gray
Write-Host "  本地副本: $integratedDir" -ForegroundColor Gray
Write-Host "  远程项目: $projectPath" -ForegroundColor Gray
Write-Host ""
Write-Host "测试步骤：" -ForegroundColor Cyan
Write-Host "1. 在本地启动打印服务: " -ForegroundColor White
Write-Host "   C:\Users\Rachel\IdeaProjects\shipping-order-print\scripts\start-print-service.bat" -ForegroundColor Gray
Write-Host "2. 访问商城订单页面: http://$RemoteHost:8899" -ForegroundColor White
Write-Host "3. 点击'打印出货单'按钮测试" -ForegroundColor White
Write-Host ""
