@echo off


set PROCESSOR | %SystemRoot%\system32\find "64" >NUL
set REG=reg
if not errorlevel 1 (
   if %PROCESSOR_ARCHITECTURE% == x86 (
      set REG=%WINDIR%\sysnative\reg
   )
)


setlocal EnableExtensions EnableDelayedExpansion


:: Fetch Xchange Viewer and extract locally if not installed.
:: Don't make default or plug in to browser, just user utility.
call :CheckXchange
if errorlevel 1 call :FetchPDFXchangeViewer


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: Main review script

set "VIEWERDIR=%LOCALAPPDATA%\PDF Review\"
(for /f "tokens=1,2,*" %%i in ('%REG% query "HKLM\SOFTWARE\Tracker Software\PDFViewer" /v InstallPath') do (set "VIEWERDIR=%%~k")) 2>NUL

(endlocal & set "VIEWER=%VIEWERDIR%PDFXCview.exe")

exit /b 0
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: Check system install and user install before failing check
::
:CheckXchange
::
%REG% query "HKLM\SOFTWARE\Tracker Software\PDFViewer" /v InstallPath >NUL 2>NUL && exit /b 0
if exist "%LOCALAPPDATA%\PDF Review\PDFXCView.exe" (
if exist "%LOCALAPPDATA%\PDF Review\resource.dat" (
   exit /b 0
))
:: 
exit /b 1


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::
:FetchPDFXchangeViewer
::
echo.
echo.  PDF Xchange Viewer not found, attempting to fetch portable version...
echo.
:RetryInstallXchange
mkdir "%LOCALAPPDATA%\PDF Review" 2>NUL
del "%LOCALAPPDATA%\PDF Review\pdfxcv-portable.zip" 2>NUL
wscript /e:JScript "%~dp0fetch-uri" "http://downloads.pdf-xchange.com/PDFX_Vwr_Port.zip" "%LOCALAPPDATA%\PDF Review\pdfxcv-portable.zip"
wscript /e:JScript "%~dp0unzip-pdfxcv" "%LOCALAPPDATA%\PDF Review\pdfxcv-portable.zip"
call :CheckXchange
if errorlevel 1 (
   echo.
   echo.  PDF Xchange View could not be installed automatically on your system.
   echo.  
   echo.  Review cannot proceed without it.
   echo.
   echo.  Please install manually from https://www.tracker-software.com/product/pdf-xchange-viewer
   echo.
   :: set default prefs here as user is presumably only installing for this use case.
   call :SetDefaultPrefs
	echo.
	echo Press ENTER or close this window.
   pause >NUL 2>&1
   exit 1
)

:SetDefaultPrefs
(
%REG% add "HKCU\Software\Tracker Software\PDFViewer\Prompts\ConfirmMakeDefaultPDFViewer"  /v Show /t REG_DWORD /d 0 /f
%REG% add "HKCU\Software\Tracker Software\PDFViewer\Prompts\ConfirmMakeDefaultPDFViewer"  /v UserChoice /t REG_DWORD /d 7 /f
%REG% add "HKCU\Software\Tracker Software\PDFViewer\Registration"  /v HideProFeatures /t REG_DWORD /d 1 /f
) >NUL

exit /b
