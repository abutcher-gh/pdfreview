@echo off

setlocal EnableExtensions EnableDelayedExpansion

set "HERE=%~dp0"

call "%HERE%\install-pdf-xchange-viewer"
if errorlevel 1 exit /b

set "MATERIAL=%~1"

echo.
echo.  VIEWER=%VIEWER%
echo.  MATERIAL="%MATERIAL%"
echo.

cscript //nologo "%HERE%\pdf-review.vbs" "%MATERIAL%"

echo.
echo Review session complete.  Press ENTER or close this window.
pause >NUL 2>&1
