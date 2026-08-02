@echo off
rem 주간업무보고 실행 스크립트. weekly-report.jar와 같은 폴더에 두고 더블클릭으로 실행합니다.
rem 콘솔 창 없이 백그라운드로 뜨며, 뜬 후 자동으로 기본 브라우저가 열립니다.
cd /d %~dp0
start "" javaw -jar "%~dp0weekly-report.jar"
