param()
echo 'Starting gradle build...'
gradle.bat -p 1.20.1/fabric build --no-daemon -x test
exit 
