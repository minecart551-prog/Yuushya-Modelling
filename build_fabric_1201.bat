@echo off
cd /d "C:\Users\User\Documents\GitHub\Yuushya-Modelling"
echo "Starting gradle build..."
"C:\Users\User\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat" -p 1.20.1/fabric build --no-daemon -x test
exit /b %ERRORLEVEL%

