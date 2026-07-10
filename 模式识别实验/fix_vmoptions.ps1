$vmoptionsPath = 'D:\downlowd_cloud\JavaWeb课程资料\\u8d44料\\05. 后端Web基础(数据库)\\u8d44料\\03. DataGrip安装\\Crack\\jetbra\\vmoptions\\studio.vmoptions'

# Simpler approach: use ASCII-only content to avoid encoding issues
$content = @"
-Xms128m
-Xmx1024m
-XX:ReservedCodeCacheSize=512m
-XX:+IgnoreUnrecognizedVMOptions
-XX:+UseG1GC
-XX:SoftRefLRUPolicyMSPerMB=50
-XX:CICompilerCount=2
-XX:+HeapDumpOnOutOfMemoryError
-XX:-OmitStackTraceInFastThrow
-ea
-Dsun.io.useCanonCaches=false
-Djdk.http.auth.tunneling.disabledSchemes=""
-Djdk.attach.allowAttachSelf=true
-Djdk.module.illegalAccess.silent=true
-Dkotlinx.coroutines.debug=off
-XX:ErrorFile=%USER_HOME%/java_error_in_idea_%p.log
-XX:HeapDumpPath=%USER_HOME%/java_error_in_idea.hprof

--add-opens=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
--add-opens=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED

-javaagent:D:/jetbra/ja-netfilter.jar=jetbrains
"@

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($vmoptionsPath, $content, $utf8NoBom)
Write-Host "SUCCESS"
Get-Content $vmoptionsPath
