# 快速启动本地打印服务

@echo off
title 出货单打印服务
echo ================================================
echo    出货单打印服务 - 启动中...
echo ================================================
echo.

cd /d "%~dp0..\backend"

echo [1/3] 检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Java 环境
    echo 请安装 Java 17 或更高版本
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

echo [2/3] 检查 JAR 文件...
if not exist "target\shipping-order-print-1.0.0-SNAPSHOT.jar" (
    echo JAR 文件不存在，开始构建...
    echo.
    call mvnw.cmd clean package -DskipTests
    if errorlevel 1 (
        echo 构建失败
        pause
        exit /b 1
    )
)

echo [3/3] 启动打印服务...
echo.
echo ================================================
echo 服务地址: http://localhost:8080
echo 打印机列表: http://localhost:8080/api/printers
echo.
echo 按 Ctrl+C 停止服务
echo ================================================
echo.

java -jar target\shipping-order-print-1.0.0-SNAPSHOT.jar

pause
