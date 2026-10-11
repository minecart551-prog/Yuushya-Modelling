@echo off
cd /d "C:\Users\User\Documents\GitHub\Yuushya-Modelling"
call "C:\Users\User\Documents\GitHub\Yuushya-Modelling\gradlew.bat" ":common:gen-1.20.1" ":1.20.1:fabric:build" -x test > "C:\Users\User\Documents\GitHub\Yuushya-Modelling\build_1201.log" 2>&1
echo EXITCODE=%ERRORLEVEL% >> "C:\Users\User\Documents\GitHub\Yuushya-Modelling\build_1201.log"
