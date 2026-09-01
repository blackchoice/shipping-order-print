# 为已插入的 USB 针式机（NFCP DPK700 / USB005）确保存在打印队列。
# 以管理员 PowerShell 运行更稳妥。

$ErrorActionPreference = 'Stop'
$port = 'USB005'
$name = 'NFCP DPK700'

$usb = Get-PnpDevice -ErrorAction SilentlyContinue | Where-Object {
  $_.FriendlyName -match 'NFCP DPK700|DPK700'
}
if (-not $usb) {
  Write-Host '未检测到 NFCP DPK700 USB 设备。请确认电源、USB 线，并等待系统提示音后再运行。'
  exit 1
}

Write-Host '已检测到设备:'
$usb | Format-Table Status, Class, FriendlyName -AutoSize

if (-not (Get-PrinterPort -Name $port -ErrorAction SilentlyContinue)) {
  Write-Host "端口 $port 不存在，请重新插拔打印机后再试。"
  exit 1
}

$existing = Get-Printer -Name $name -ErrorAction SilentlyContinue
if ($existing) {
  Write-Host "打印队列已存在: $name -> $($existing.PortName) / $($existing.DriverName)"
} else {
  $driver = 'Microsoft IPP Class Driver'
  Add-Printer -Name $name -DriverName $driver -PortName $port
  Write-Host "已创建队列: $name ($driver @ $port)"
  Write-Host '建议再安装富士通 DPK700H 官方驱动，并在打印机属性中把驱动换成官方型号。'
}

Get-Printer -Name $name | Format-List Name, DriverName, PortName, PrinterStatus, Datatype
Write-Host '完成后请重启 Spring Boot，前端「刷新」打印机列表，选择 NFCP DPK700。'
