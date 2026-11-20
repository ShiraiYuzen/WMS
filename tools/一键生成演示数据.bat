@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
cd /d "%~dp0.."

echo 请按任意键开始...
pause >nul

set "PYCMD="
if defined PYTHON if not "%PYTHON%"=="" set "PYCMD=%PYTHON%"
if not defined PYCMD (
  where py >nul 2>&1 && set "PYCMD=py -3"
)
if not defined PYCMD (
  where python >nul 2>&1 && set "PYCMD=python"
)
if not defined PYCMD (
  echo 未找到 Python。请先安装 Python 3.8 或更高版本。
  echo 请按任意键继续...
  pause >nul
  exit /b 127
)

%PYCMD% "%~dp0gen-demo-data.py" %*
set "EC=%ERRORLEVEL%"
echo 请按任意键继续...
pause >nul
exit /b %EC%
