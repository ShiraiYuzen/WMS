@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"

cd /d "%~dp0.."

echo.
echo ========================================
echo   WMS 演示数据一键生成
echo ========================================
echo 将从库里最后一张订单续写到现在。数据已是最新时不会改动。
echo.
echo 请按任意键开始...
pause >nul

set "PYCMD="
if defined PYTHON (
  set "PYCMD=%PYTHON%"
)
if not defined PYCMD (
  where py >nul 2>&1 && set "PYCMD=py -3"
)
if not defined PYCMD (
  where python >nul 2>&1 && set "PYCMD=python"
)
if not defined PYCMD (
  echo.
  echo 未找到 Python。请先安装 Python 3.8 或更高版本，或设置 PYTHON 为解释器路径。
  echo.
  echo 请按任意键继续...
  pause >nul
  exit /b 127
)

%PYCMD% "%~dp0gen-demo-data.py" %*
set "EC=%ERRORLEVEL%"

echo.
if "%EC%"=="0" (
  echo 生成结束。
) else (
  echo 生成失败，退出码：%EC%
)
echo 请按任意键继续...
pause >nul
exit /b %EC%
