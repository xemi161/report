# QA E2E 전용 앱 기동 (포트 9411).
#
# 사용자의 실제 H2 파일 DB(~/.weekly-report/data/db)는 절대 건드리지 않는다 —
# in-memory DB + ddl-auto=create + qa_seed.sql 로 매번 깨끗한 상태에서 시작한다.
#
# ⚠️ 경로에 한글("상화")이 있어 이 파일 안에 절대경로를 문자열로 박으면 안 된다.
#    Windows PowerShell 5.1은 BOM 없는 UTF-8 .ps1을 ANSI로 읽어 한글이 깨지고
#    Set-Location이 PathNotFound로 실패한다. 그래서 $PSScriptRoot로만 경로를 구한다.
$ErrorActionPreference = 'Continue'
$here = $PSScriptRoot
$repo = (Resolve-Path "$here\..\..").Path
$seed = "$here\qa_seed.sql".Replace('\', '/')

Set-Location $repo
& .\gradlew.bat bootRun --console=plain "--args=--server.port=9411 --spring.datasource.url=jdbc:h2:mem:qaTest;DB_CLOSE_DELAY=-1 --spring.jpa.hibernate.ddl-auto=create --spring.jpa.defer-datasource-initialization=true --spring.sql.init.mode=always --spring.sql.init.data-locations=file:$seed --spring.sql.init.encoding=UTF-8"
