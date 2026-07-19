@echo off
setlocal
cd /d "%~dp0"
mvn.cmd "spring-boot:run" "-Dspring-boot.run.profiles=dev"
