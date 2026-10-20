@echo off
rem Windows entry point for cmd.exe: setup.cmd <step> [options]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
