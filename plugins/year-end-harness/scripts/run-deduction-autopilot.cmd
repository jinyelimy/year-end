@echo off
setlocal
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

if /I "%~1"=="--status" goto status
if /I "%~1"=="status" goto status
if /I "%~1"=="--ralph" goto ralph
if /I "%~1"=="ralph" goto ralph

echo Starting year-end deduction one-shot autopilot...
echo Repository: %CD%
echo.
echo The autopilot contract is:
echo   plugins/year-end-harness/automation/one-shot-autopilot.md
echo.
echo Mode: non-interactive omx exec
echo Use --ralph from a real terminal if you want interactive Ralph persistence.
echo.

omx.cmd exec --dangerously-bypass-approvals-and-sandbox -C "%REPO_ROOT%" "진행되지 않은 과정을 자동화에 따라 전부 구현해줘. 시작 전에 plugins/year-end-harness/automation/one-shot-autopilot.md 를 읽고 그 계약을 그대로 실행해. docs/notes/command_list.md 와 plugins/year-end-harness/automation/backlog.json 을 기준으로 남은 공제 슬라이스를 하나씩 구현/검증/커밋하고, BACKLOG_EMPTY OR HUMAN_REVIEW_REQUIRED OR PHASE1_REENTRY_REQUIRED OR FAIL 중 하나가 될 때만 멈춰."
exit /b %ERRORLEVEL%

:ralph
echo Starting year-end deduction one-shot autopilot...
echo Repository: %CD%
echo.
echo The autopilot contract is:
echo   plugins/year-end-harness/automation/one-shot-autopilot.md
echo.
echo Mode: interactive omx ralph
echo.
omx.cmd ralph --no-deslop "진행되지 않은 과정을 자동화에 따라 전부 구현해줘. 시작 전에 plugins/year-end-harness/automation/one-shot-autopilot.md 를 읽고 그 계약을 그대로 실행해. docs/notes/command_list.md 와 plugins/year-end-harness/automation/backlog.json 을 기준으로 남은 공제 슬라이스를 하나씩 구현/검증/커밋하고, BACKLOG_EMPTY OR HUMAN_REVIEW_REQUIRED OR PHASE1_REENTRY_REQUIRED OR FAIL 중 하나가 될 때만 멈춰."
exit /b %ERRORLEVEL%

:status
echo === Deduction Autopilot Status ===
python plugins/year-end-harness/automation/scripts/check-backlog-empty.py
echo.
echo === Next Slice ===
python plugins/year-end-harness/automation/scripts/pick-next-slice.py
exit /b 0
