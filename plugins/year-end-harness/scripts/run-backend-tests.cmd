@echo off
setlocal

for %%I in ("%~dp0..\..\..") do set "REPO_ROOT=%%~fI"
set "ARTIFACT=backend\build\reports\tests\test\index.html"

pushd "%REPO_ROOT%\backend" >nul
call .\gradlew.bat test
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul

echo === HARNESS RESULT ===
if "%EXIT_CODE%"=="0" (
  echo STATUS   : success
  echo SUMMARY  : Full backend regression passed.
  echo ARTIFACTS: %ARTIFACT%
  echo NEXT     : Continue to frontend build or QA verification as needed.
) else (
  echo STATUS   : error
  echo SUMMARY  : Full backend regression failed.
  echo ARTIFACTS: %ARTIFACT%
  echo NEXT     : Review the failing suite before moving to QA.
)
echo ======================

exit /b %EXIT_CODE%
