#!/bin/bash
targetDir='/usr/local/dev/sync-wsp/bdt-metric-stitcher'
mkdirCommand="sudo mkdir -p $targetDir/target"
chmodCommand="sudo chmod 777 $targetDir/target"
rmCommand="sudo rm -rf $targetDir/target"
echoCommand="ls $targetDir/"
$mkdirCommand && $chmodCommand && echo "sync-clean execute done !"