@ECHO OFF
cd /d %~dp0
Set StartInDirectory=%CD%
setLocal EnableDelayedExpansion
${java.win.path} -jar easycie-gui-3.0.2-SNAPSHOT-jfx.jar

