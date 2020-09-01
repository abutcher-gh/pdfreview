' read by installer script and used for
' interface validation against server
PDFReviewClientVersion = 0.6

' Utilities
'==============================================================================
'
Set sh = WScript.CreateObject("WScript.Shell")
Set fso = WScript.CreateObject("Scripting.FileSystemObject")
Set env = sh.Environment("Process")
Set dbstream = WScript.CreateObject("ADODB.Stream") ' for binary to string conversion (reused)
Set req = CreateObject("WinHttp.WinHttpRequest.5.1") ' web client (reused)
Set xml = CreateObject("Msxml2.DOMDocument") ' xml parser (reused)
'Set xmlrpc = CreateObject("pocketXMLRPC.Factory") ' xmlrpc client (reused)

Const adStateClosed = 0
Const adStateOpen = 1

Const adTypeText = 2
Const adTypeBinary = 1

Function BinaryToString(bytes)

   If LenB(bytes) = 0 Then Exit Function

   With dbstream
      If .State = adStateOpen Then .State = adStateClosed
      .Type = adTypeBinary
      .Open
      .Write bytes
      .position = 0
      .Type = adTypeText
      .CharSet = "x-user-defined"
      BinaryToString = .ReadText
      .Close
   End With

End Function


' Script arguments
'==============================================================================
'
Dim reviewurl
' decomposes into:
Dim webdir, query
' query contains:
Dim tag, user, page, cookie, token


' Parse arguments
'==============================================================================
'
if WScript.Arguments.length <> 1 then
   wscript.echo "pdfreview URL required as only argument."
   wscript.quit 10
end if

reviewurl = WScript.Arguments.Item(0)

scheme = "pdfreview:"
schemelen = len(scheme)

if left(reviewurl, schemelen) <> scheme then
   wscript.echo "URL must use scheme 'pdfreview'." & vbCRLF & vbCRLF
   wscript.echo "Error parsing '" & reviewurl & "'"
   wscript.quit 20
end if

querystart = instr(reviewurl, "?")

webdir = mid(reviewurl, schemelen+1, querystart-schemelen-1)
query = unescape(mid(reviewurl, querystart+1))

pdfreviewurl = left(webdir, instr(webdir, "/plugins/")-1) & "/plugins/pdfreview"

Set queryRegex = New RegExp
queryRegex.IgnoreCase = False
queryRegex.Global = True
queryRegex.Pattern = "([^&]+)&?"

set matches = queryRegex.execute(query)
for each match in matches
   s = match.submatches(0)
   eq = instr(s, "=")
   param_name = left(s, eq-1)
   param_value = mid(s, eq+1)
   if param_name = "tag" then tag = param_value
   if param_name = "cookie" then cookie = param_value
   if param_name = "token" then token = param_value
   if param_name = "page" then page = param_value
next

user = env("USERNAME")

id = left(tag, instr(tag, "-")-1)

pdffile = tag & ".pdf"


' Establish working directory
'==============================================================================
'
workdir = env("APPDATA") + "\pdfreview\"
if not fso.FolderExists(workdir) then fso.CreateFolder workdir
sh.CurrentDirectory = workdir


' Client version check
'==============================================================================
'
req.Open "GET", pdfreviewurl & "/client-check.action?clientVersion=" & PDFReviewClientVersion, False
req.SetRequestHeader "Content-type", "text/xml"
req.SetRequestHeader "Translate", "f"
req.SetRequestHeader "Cookie", cookie
req.Send

If (req.status \ 100) = 2 Then

   if req.responsetext <> "" then

      msg = vbCRLF & "*** Client version check failed ***" & vbCRLF

      wscript.echo msg
      wscript.echo req.responsetext
      wscript.echo msg
      wscript.quit 9

   end if

Else

   msg = vbCRLF & "*** Failed to run client version check against server ***" & vbCRLF

   wscript.echo msg
   wscript.echo "Status: " & req.status
   wscript.echo "Status text: " & req.statustext
   wscript.echo "Response text: " & req.responsetext
   wscript.echo msg

   wscript.quit 7

End if

' Enumerate comment files
'==============================================================================
'

ls = "<?xml version='1.0'?>" & _
     "<a:propfind xmlns:a='DAV:'>" & _
      "<a:prop>" & _
       "<a:displayname/>" & _
      "</a:prop>" & _
     "</a:propfind>"

req.open "PROPFIND", webdir, false
req.SetRequestHeader "Content-type", "text/xml"
req.SetRequestHeader "Translate", "f"
req.SetRequestHeader "Cookie", cookie
req.send ls

If req.status = 207 Then

   xml.loadXML req.responseText

   set names = xml.getElementsByTagName("D:displayname")

   taglen = len(tag)

   files = pdffile

   for each n in names
      s = n.text
      if left(s, taglen) = tag and right(s, 4) = ".fdf" then
         files = files & "|" & s
      end if
   next

   files = split(files, "|")

Else

   ' Display the status, status text, and response text.
   wscript.echo "Status: " & req.status
   wscript.echo "Status text: " & req.statustext
   wscript.echo "Response text: " & req.responsetext

End If

for each x in files

   wscript.echo "Downloading '" & x & "'..."

   req.Open "GET", webdir & "/" & x, False
   req.SetRequestHeader "Content-type", "text/xml"
   req.SetRequestHeader "Translate", "f"
   req.SetRequestHeader "Cookie", cookie
   req.Send

   set out = fso.CreateTextFile(x, true, false)
   out.Write BinaryToString(req.responseBody)
   out.Close
   set out = nothing

next

' assume_changed is true if starting from a previously uncommited file
assume_changed = false

if fso.FileExists("pending." & pdffile) then
   ans = MsgBox("" _
       & "A previously updated copy of this document was found locally:" & vbCrLf _
       & "  " & sh.CurrentDirectory & "\pending." & pdffile & vbCrLf _
       & vbCrLf _
       & "Do you wish to use that version as the basis for comments (to" & vbCrLf _
       & "preserve comments made in a prior update session)?" & vbCrLf _
       , vbYesNo+vbQuestion, "Recover uncommitted comments?")
   if ans = vbYes then
      fso.CopyFile "pending." & pdffile, pdffile, true
      assume_changed = true
   end if
end if


' Begin watching the confluence review page
'==============================================================================
'
req.Open "GET", pdfreviewurl & "/watch-page.action?page=" & page & "&id=" & id, False
req.SetRequestHeader "Content-type", "application/octet-stream"
req.SetRequestHeader "Translate", "f"
req.SetRequestHeader "Cookie", cookie
req.Send


' Create javascript to import annotations
'==============================================================================
'
' XXX: This should hook the DidSave event to export annotations to FDF on user
' XXX: save, but it doesn't seem to work in PDF Xchange viewer.
' XXX:
' XXX: Hence there is some rather ugly double load and diff thing
' XXX: going on here.
' XXX:
' XXX: The DidSave hook is left in as it doesn't cause a fault -- just
' XXX: doesn't work.
' XXX:
' XXX: The workaround is to run interactive to merge the incoming
' XXX: annotations in, save the file and a base annotation file and
' XXX: all the user to update the file and exit manually.  Then re-run
' XXX: headless to extract the annotations if the file was updated.
'
comment_script = tag & "-comment.js"
base_fdf = tag & "-base.fdf"
user_fdf = tag & "-" & user & ".fdf"

set out = fso.CreateTextFile(comment_script, true, false)

for i = 1 to ubound(files)
  out.WriteLine "this.importAnFDF(""" & files(i) & """); "
  out.WriteLine "app.execMenuItem(""Save"", this); "
  out.WriteLine "this.exportAsFDF({allFields: true, bAnnotations: true, cPath: ""test.fdf"", bFlags: true}); "
next
out.WriteLine "this.exportAsFDF({allFields: true, bAnnotations: true, cPath: """ & base_fdf & """, bFlags: true}); "
out.WriteLine "this.setAction(""DidSave"", 'this.exportAsFDF({allFields: true, bAnnotations: true, cPath: """ & user_fdf & """, bFlags: true});'); "
'sh.Run "%VIEWER% ""/runjs:newinst&showui=yes&log=yes"" """ & comment_script & """ """ & pdffile & """", 1, true

time_spent_secs = 0
time_spent_mins = 0

' ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
' begin workaround -- see above
out.Close
set out = nothing
' run interactive to allow user update
sh.Run """%VIEWER%"" ""/runjs:newinst&showui=yes&log=yes"" """ & comment_script & """ """ & pdffile & """", 1, true
Set baseAnnot = fso.GetFile(base_fdf)
Set newPdf = fso.GetFile(pdffile)
time_spent_secs = DateDiff("s", baseAnnot.DateLastModified, newPdf.DateLastModified)
if time_spent_secs > 0 then
   time_spent_mins = DateDiff("m", baseAnnot.DateLastModified, newPdf.DateLastModified)

   wscript.echo "Updated - Time spent " & time_spent_mins & " minutes"

   comment_script = replace(comment_script, "-comment", "-export")
   set out = fso.CreateTextFile(comment_script, true, false)
   out.WriteLine "this.exportAsFDF({allFields: true, bAnnotations: true, cPath: """ & user_fdf & """, bFlags: true}); "
   out.WriteLine "app.execMenuItem(""Quit""); "
   out.Close
   set out = nothing
   ' run headless for export
   sh.Run """%VIEWER%"" ""/runjs:newinst&showui=no&log=yes"" """ & comment_script & """ """ & pdffile & """", 1, true

else
   wscript.echo "No comments added"
end if
' end workaround -- see above
' ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

modified = assume_changed

rc = 1

' diff fdf files (except date stamps)
'
if time_spent_secs > 0 then

   const ForReading = 1
   set baseFile = fso.OpenTextFile(base_fdf, ForReading)
   set userFile = fso.OpenTextFile(user_fdf, ForReading)

   Do Until baseFile.AtEndOfStream or userFile.AtEndOfStream
      s1 = baseFile.Readline
      s2 = userFile.Readline
      if left(s1, 6) <> "/M (D:" then
         if s1 <> s2 then
            modified = true
            exit do
         end if
      end if
   Loop
   baseFile.close
   userFile.close
   set baseFile = nothing
   set userFile = nothing

end if

if modified = true then
   wscript.echo "Annotations updated, uploading..."

   fso.CopyFile pdffile, "pending." & pdffile, true

   ok = false

   With dbstream
      If .State = adStateOpen Then .State = adStateClosed
      .Type = adTypeBinary
      .Open
      .LoadFromFile(user_fdf)
   End With

   if dbstream.EOS then
      wscript.echo "Error reading '" & user_fdf & "'."
   else

      req.Open "PUT", webdir & "/" & user_fdf, False
      req.SetRequestHeader "Content-type", "application/octet-stream"
      req.SetRequestHeader "Translate", "f"
      req.SetRequestHeader "Cookie", cookie
      req.Send dbstream

      If (req.status \ 100) = 2 Then

         wscript.echo "Upload successful."
         fso.DeleteFile "pending." & pdffile, true
         rc = 0

      Else

         wscript.echo "Upload error." & vbCRLF

         diag = "" _
            & "PDF Review Scripts failed to upload your comments.  This is" & vbCRLF _
            & "likely due to your authentication token having expired during" & vbCRLF _
            & "your review session." & vbCRLF _
            & vbCRLF _
            & "Your comments have been saved locally.  Please refresh the" & vbCRLF _
            & "Confluence review page to update authentication details" & vbCRLF _
            & "and click the review link to retry the submission." & vbCRLF _

         wscript.echo diag
         MsgBox diag, vbExclamation, "Authentication token has expired"

      End If

   end if

   dbstream.close

else
   wscript.echo "No annotations updated."
end if


Set req = Nothing

Set dbstream = Nothing

wscript.quit rc


''' HACKY IE PASSWORD REQUESTER -- UNUSED, THANKFULLY

strPw = GetPassword( "Please enter your password:")
msgbox  "Your password is: " & strPw

Function GetPassword( myPrompt )
' This function uses Internet Explorer to
' create a dialog and prompt for a password (it's super secret).

    Dim objIE
    ' Create an IE object (I guess you'll have to)
    Set objIE = CreateObject( "InternetExplorer.Application" )
    ' specify  the IE  settings
    objIE.Navigate "about:blank"
    objIE.Document.Title = "Password"
    objIE.ToolBar        = False
    objIE.Resizable      = False
    objIE.StatusBar      = False
    objIE.Width          = 300
    objIE.Height         = 180
    ' Center the dialog window on the screen (front and center!)
    With objIE.Document.ParentWindow.Screen
        objIE.Left = (.AvailWidth  - objIE.Width ) \ 2
        objIE.Top  = (.Availheight - objIE.Height) \ 2
    End With

    ' Insert the HTML code to prompt for your super secret password'
    objIE.Document.Body.InnerHTML = "<DIV align='center'><P>" & myPrompt _
                                  & "</P>" & vbCrLf _
                                  & "<P><INPUT TYPE='password' SIZE= '20'" _
                                  & "ID='Password'></P>" & vbCrLf _
                                  & "<P><INPUT TYPE='hidden' ID='OK'" _
                                  & "NAME='OK' VALUE='0'>" _
                                  & "<INPUT TYPE='submit' VALUE='OK'" _
                                  & "OnClick='VBScript:OK.Value=1'></P></DIV>"
    ' Make the window visible (If you must *sigh*)
    objIE.Visible = True
    ' Wait till the OK button has been clicked (uh huh...)
    Do While objIE.Document.All.OK.Value = 0
        WScript.Sleep 200
    Loop
    ' Read the password from the dialog window (evil thoughts on this method)
    GetPassword = objIE.Document.All.Password.Value
    ' Close and release the object
    objIE.Quit
    Set objIE = Nothing
End Function

