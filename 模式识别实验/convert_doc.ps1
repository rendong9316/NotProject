$word = New-Object -ComObject Word.Application
$word.Visible = $false
$docPath = "D:\Desktop\模式识别实验\模式识别上机实验要求2026.doc"
$newPath = "D:\Desktop\模式识别实验\模式识别上机实验要求2026_converted.docx"
$doc = $word.Documents.Open($docPath)
$doc.SaveAs2($newPath, 16)
$doc.Close()
$word.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
Write-Host "Conversion completed successfully"
