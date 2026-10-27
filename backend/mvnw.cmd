@echo off
setlocal
set "MVNW_DIR=%~dp0"
cd /d "%MVNW_DIR%"
java "-Dmaven.multiModuleProjectDirectory=." -cp ".mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
