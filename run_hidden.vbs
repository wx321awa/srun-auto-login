' 无窗口静默运行 srun_login.py（给 Windows 计划任务用）
Dim fso, shell, here
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
here = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = here

On Error Resume Next
shell.Run "pythonw """ & here & "\srun_login.py""", 0, False
If Err.Number <> 0 Then
    Err.Clear
    shell.Run "pyw """ & here & "\srun_login.py""", 0, False
End If