# 测试本地打印服务连接

Write-Host "=== 测试打印服务连接 ===" -ForegroundColor Cyan
Write-Host ""

$serviceUrl = "http://localhost:8080"

# 1. 测试服务是否可访问
Write-Host "[1/4] 检查服务状态..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$serviceUrl/api/printers" -UseBasicParsing -TimeoutSec 5
    Write-Host "✓ 服务在线 (HTTP $($response.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "✗ 服务离线或无法访问" -ForegroundColor Red
    Write-Host "请确保打印服务正在运行: .\scripts\start-print-service.bat" -ForegroundColor Yellow
    exit 1
}

# 2. 获取打印机列表
Write-Host ""
Write-Host "[2/4] 获取打印机列表..." -ForegroundColor Yellow
try {
    $printersJson = Invoke-RestMethod -Uri "$serviceUrl/api/printers" -Method Get
    $printers = $printersJson.printers

    if ($printers.Count -gt 0) {
        Write-Host "✓ 找到 $($printers.Count) 台打印机:" -ForegroundColor Green
        foreach ($printer in $printers) {
            Write-Host "  - $printer" -ForegroundColor Gray
        }
    } else {
        Write-Host "✗ 未找到可用打印机" -ForegroundColor Red
        Write-Host "请检查打印机是否正确安装" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ 获取打印机列表失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. 测试预览 API
Write-Host ""
Write-Host "[3/4] 测试预览 API..." -ForegroundColor Yellow

$testOrder = @{
    companyTitle = "怀化市兴隆农业开发有限公司出货单"
    customerName = "本部食堂"
    orderNo = "TEST-001"
    date = (Get-Date -Format "yyyy-MM-dd")
    deliverer = "测试"
    receiver = "测试"
    lines = @(
        @{
            productName = "测试商品"
            unit = "件"
            quantity = 1
            unitPrice = 10.0
            remark = ""
        }
    )
} | ConvertTo-Json -Depth 10

try {
    $layoutResponse = Invoke-RestMethod -Uri "$serviceUrl/api/print/shipping-order/layout" `
        -Method Post `
        -Body $testOrder `
        -ContentType "application/json"

    Write-Host "✓ 预览 API 正常" -ForegroundColor Green
    Write-Host "  版式: $($layoutResponse.pageWidthChars) 列 × $($layoutResponse.totalLines) 行" -ForegroundColor Gray
} catch {
    Write-Host "✗ 预览 API 失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 4. 测试 CORS 配置
Write-Host ""
Write-Host "[4/4] 测试 CORS 配置..." -ForegroundColor Yellow

$headers = @{
    "Origin" = "http://119.29.98.147:8899"
    "Access-Control-Request-Method" = "POST"
    "Access-Control-Request-Headers" = "content-type"
}

try {
    $corsResponse = Invoke-WebRequest -Uri "$serviceUrl/api/printers" `
        -Method Options `
        -Headers $headers `
        -UseBasicParsing

    $allowOrigin = $corsResponse.Headers['Access-Control-Allow-Origin']

    if ($allowOrigin) {
        Write-Host "✓ CORS 配置正常" -ForegroundColor Green
        Write-Host "  允许来源: $allowOrigin" -ForegroundColor Gray
    } else {
        Write-Host "⚠ CORS 头缺失，跨域访问可能失败" -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠ CORS 预检请求失败" -ForegroundColor Yellow
}

# 总结
Write-Host ""
Write-Host "=== 测试完成 ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "服务地址: $serviceUrl" -ForegroundColor Green
Write-Host "可以在商城页面中使用 ShippingPrintClient.printOrder() 调用打印功能" -ForegroundColor Green
Write-Host ""
Write-Host "下一步:" -ForegroundColor Yellow
Write-Host "1. 在商城页面引入 shipping-print-client.js" -ForegroundColor Gray
Write-Host "2. 在订单详情页添加打印按钮" -ForegroundColor Gray
Write-Host "3. 调用 ShippingPrintClient.printOrder(orderData)" -ForegroundColor Gray
Write-Host ""
