
rem --- Launch URL Service ---
start "UrlQueue" cmd /k "cd /d "%~dp0" && mvn -q -DskipTests exec:java -Dexec.mainClass=projetosd.UrlQueue"
timeout /t 1 >nul

rem --- Launch Gateway ---
start "Gateway" cmd /k "cd /d "%~dp0" && mvn -q -DskipTests exec:java -Dexec.mainClass=projetosd.Gateway"
timeout /t 1 >nul

rem --- Launch Barrel ---
start "Barrel" cmd /k "cd /d "%~dp0" && mvn -q -DskipTests exec:java -Dexec.mainClass=projetosd.Barrel"
timeout /t 1 >nul

rem --- Launch Downloader ---
start "Downloader" cmd /k "cd /d "%~dp0" && mvn -q -DskipTests exec:java -Dexec.mainClass=projetosd.Downloader"
timeout /t 1 >nul

rem --- Launch Client in a new terminal (interactive) ---
start "Client" cmd /k "cd /d "%~dp0" && mvn -q -DskipTests exec:java -Dexec.mainClass=projetosd.Client"

