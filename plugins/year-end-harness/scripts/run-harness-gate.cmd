@echo off
setlocal

python "%~dp0run-harness-gate.py" %*
set "EXIT_CODE=%ERRORLEVEL%"

exit /b %EXIT_CODE%
