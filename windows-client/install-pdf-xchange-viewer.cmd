@echo off

setlocal EnableExtensions EnableDelayedExpansion


set PROCESSOR | %SystemRoot%\system32\find "64" >NUL
if errorlevel 1 (
   set ARCH=x86
   set APPID=3A6F4A31-8CFD-46B4-8385-E1F384DB121E
   set REG=reg
) else (
   set ARCH=x64
   set APPID=9ED333F8-3E6C-4A38-BAFA-728454121CDA
   set REG=%WINDIR%\sysnative\reg
)


:: Fetch Xchange Viewer and install if not present.
:: Don't make default or plug in to browser, just user utility.
call :CheckXchange
if errorlevel 1 call :InstallXchange


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: Main review script

for /f "tokens=1,2,*" %%i in ('%REG% query "HKLM\SOFTWARE\Tracker Software\PDFViewer" /v InstallPath') do (set VIEWERDIR=%%~sk)

(endlocal & set VIEWER=%VIEWERDIR%PDFXCview.exe)

exit /b 0
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: Check system install and user install before failing check
::
:CheckXchange
::
%REG% query "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{%APPID%}" /v DisplayVersion >NUL 2>NUL
:: 
exit /b


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::
:InstallXchange
::
echo.
echo.  Xchange Viewer not found, installing...
echo.
:RetryInstallXchange
start /wait msiexec /i "http://c1236872.r72.cf0.rackcdn.com/PXCViewer_%ARCH%.msi" ADDLOCAL="F_Viewer" ASSOC="0" SHOWINBROWSERS="0" CREATEDESKTOPICON="0" /passive /norestart
if errorlevel 1 (

   echo.  Installation failed.
   echo.

   set /p "YN=> Do you want to review Internet Options? [y/n] "
   if :!YN! == :y (
      echo.
      echo.  Starting Internet Options Control Panel...
      echo.
      start /wait inetcpl.cpl
   )

   set /p "YN=> Attempt install again? [y/n] "
   if :!YN! == :y goto :RetryInstallXchange
)

call :CheckXchange
if errorlevel 1 (
   echo.
   echo.  Xchange is not installed.  Review cannot proceed.
   echo.
   pause
   exit 1
)

:: set default prefs
(
%REG% add "HKCU\Software\Tracker Software\PDFViewer\Prompts\ConfirmMakeDefaultPDFViewer"  /v Show /d 0 /f
%REG% add "HKCU\Software\Tracker Software\PDFViewer\Prompts\ConfirmMakeDefaultPDFViewer"  /v UserChoice /d 7 /f
%REG% add "HKCU\Software\Tracker Software\PDFViewer\Registration"  /v HideProFeatures /t REG_DWORD /d 1 /f
) >NUL

exit /b

