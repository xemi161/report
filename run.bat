@echo off
rem 주간업무보고 실행 스크립트. weekly-report.jar와 같은 폴더에 두고 더블클릭으로 실행합니다.
rem 콘솔 창 없이 백그라운드로 뜨며, 뜬 후 자동으로 기본 브라우저가 열립니다.
rem
rem 포트를 9099로 못박는 이유:
rem   개발 중에는 IDE가 application.yml의 포트로 앱을 띄운다. 배포본까지 같은 포트를 쓰면
rem   WSL의 개발 서버와 Windows의 이 배포본이 충돌하는데, 이때 브라우저의 localhost는
rem   항상 Windows 쪽(=이 배포본)으로 붙는다. javaw라 콘솔 창도 없어서 배포본이 떠 있는 줄
rem   모른 채 "코드를 고쳤는데 화면이 안 바뀐다"고 헤매기 딱 좋다(실제로 겪음).
rem   여기서 포트를 따로 못박아 두면 application.yml을 어떻게 바꾸든 둘이 겹치지 않는다.
cd /d %~dp0
start "" javaw -jar "%~dp0weekly-report.jar" --server.port=9099
