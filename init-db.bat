@echo off
setlocal
set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not exist "%MYSQL%" set "MYSQL=mysql"
echo Initializing database smart_fridge...
echo Please enter MySQL root password when prompted.
"%MYSQL%" -u root -p < "%~dp0docs\schema.sql"
if %errorlevel%==0 (
  echo.
  echo Done: database smart_fridge created.
) else (
  echo.
  echo Failed. Check MySQL password or service status.
)
pause
