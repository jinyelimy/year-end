@echo off
setlocal

for %%I in ("%~dp0..\..\..") do set "REPO_ROOT=%%~fI"
set "ARTIFACT=backend\build\reports\tests\test\index.html"

pushd "%REPO_ROOT%\backend" >nul
call gradlew.bat test --tests "*HometaxPdf*"
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul

echo === HARNESS RESULT ===
if "%EXIT_CODE%"=="0" (
  echo STATUS   : success
  echo SUMMARY  : Hometax parser tests passed.
  echo ARTIFACTS: %ARTIFACT%
  echo NEXT     : Proceed to backend regression or focused implementation review.
) else (
  echo STATUS   : error
  echo SUMMARY  : Hometax parser tests failed.
  echo ARTIFACTS: %ARTIFACT%
  echo NEXT     : Inspect the HTML report and failing test output before continuing.
)
echo ======================

exit /b %EXIT_CODE%
