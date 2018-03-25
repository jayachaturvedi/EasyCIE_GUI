#!/usr/bin/env bash
version="2.0.6"
cd ..
base="$PWD"
if [ ! -d "$base/target/release" ]; then
  mkdir target/release
fi
jdkbase=$base/../../../Downloads/jdks



cd target/deploy
sed -i 's/jdk1.8\/bin\/java/java/g' run_gui
sed -i 's/jdk1.8\\bin\\java/java/g' run_gui.bat
zip -r "../release/EasyCIE_${version}_wo_jdk.zip" "./"
sed -i 's/java/jdk1.8\/bin\/java/g' run_gui
sed -i 's/java/jdk1.8\\bin\\java/g' run_gui.bat
zip -r "../release/EasyCIE_${version}_win_jdk.zip" "./"
zip -r "../release/EasyCIE_${version}_mac_jdk.zip" "./"
zip -r "../release/EasyCIE_${version}_linux_jdk.zip" "./"
cd $jdkbase/linux/
zip -ur "$base/target/release/EasyCIE_${version}_linux_jdk.zip" "./"
cd $jdkbase/win/
zip -ur "$base/target/release/EasyCIE_${version}_win_jdk.zip" "./"
cd $jdkbase/mac/
zip -ur "$base/target/release/EasyCIE_${version}_mac_jdk.zip" "./"
