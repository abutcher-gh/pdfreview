; Script generated by the Inno Setup Script Wizard.
; SEE THE DOCUMENTATION FOR DETAILS ON CREATING INNO SETUP SCRIPT FILES!


#define MyAppName "PDF Review Scripts"
#define MyAppPublisher "Adam Butcher"
#define MyAppURL "https://www.assembla.com/spaces/ajbhome-process/wiki"
#define MySupportURL "https://www.assembla.com/spaces/ajbhome-process/tickets"

; Parse version out of actual script
;
#define FileHandle
#define FileLine
#define MyAppVersion
#for {FileHandle = FileOpen("pdf-review.vbs"); \
  MyAppVersion == "" && FileHandle && !FileEof(FileHandle); FileLine = FileRead(FileHandle)} \
  MyAppVersion = (Pos("PDFReviewClientVersion", FileLine) == 1 ? \
    Trim(Copy(FileLine, Pos("=", FileLine)+1)) : "")
#if FileHandle
  #expr FileClose(FileHandle)
#endif
#undef FileLine
#undef FileHandle
#pragma message "Version from vbs = " + MyAppVersion


[Setup]
; NOTE: The value of AppId uniquely identifies this application.
; Do not use the same AppId value in installers for other applications.
; (To generate a new GUID, click Tools | Generate GUID inside the IDE.)
AppId={{C9493B76-247E-45E4-9B8E-6A2AB855444A}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
VersionInfoProductName={#MyAppName} Setup
VersionInfoCopyright={#MyAppPublisher}
VersionInfoProductVersion={#MyAppVersion}
VersionInfoVersion={#MyAppVersion}.0.0
;AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MySupportURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={pf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=.
OutputBaseFilename=pdf-review-setup
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest

[Files]
Source: "pdf-review.vbs"; DestDir: "{app}"; Flags: ignoreversion
Source: "pdf-review.cmd"; DestDir: "{app}"; Flags: ignoreversion
Source: "install-pdf-xchange-viewer.cmd"; DestDir: "{app}"; Flags: ignoreversion
Source: "fetch-uri"; DestDir: "{app}"; Flags: ignoreversion
Source: "unzip-pdfxcv"; DestDir: "{app}"; Flags: ignoreversion
Source: "{cmd}"; DestDir: "{app}"; DestName: "PDF Review.exe"; Flags: ignoreversion external

[Icons]
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"

[Code]
function GetCommandLine( _ : string ) : string;
begin
   result := '"' + ExpandConstant('{app}') + '\PDF Review" /c ""' + ExpandConstant('{app}') + '\pdf-review" """%1""""';
end;

[Registry]

Root: HKCU; Subkey: Software\Classes\pdfreview; ValueType: string; ValueData: "URL:PDF Review Scheme"; Flags: uninsdeletekey
Root: HKCU; Subkey: Software\Classes\pdfreview; ValueType: string; ValueName: "URL Protocol"
Root: HKCU; Subkey: Software\Classes\pdfreview\shell\open\command; ValueType: string; ValueData: {code:GetCommandLine}
