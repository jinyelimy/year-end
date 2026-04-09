@echo off
setlocal

for %%I in ("%~dp0..\..\..") do set "REPO_ROOT=%%~fI"
set "ARTIFACT=frontend\.next"

pushd "%REPO_ROOT%\frontend" >nul
call npm.cmd run build
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul

echo === HARNESS RESULT ===
if "%EXIT_CODE%"=="0" (
  echo STATUS   : success
  echo SUMMARY  : Frontend production build passed.
  echo ARTIFACTS: %ARTIFACT%
  echo NEXT     : Continue to QA verification or artifact review.
) else (
  echo STATUS   : error
  echo SUMMARY  : Frontend production build failed.
  echo ARTIFACTS: %ARTIFACT%
  echo NEXT     : Fix the build error before advancing the harness phase.
)
echo ======================

exit /b %EXIT_CODE%
