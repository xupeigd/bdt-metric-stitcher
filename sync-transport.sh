# !/bin/bash
targetHost=page.quicksand.com
targetDir=/usr/local/dev/sync-wsp/bdt-metric-stitcher
scpCommand="scp ./target/bdt-metric-stitcher.jar $targetHost:$targetDir/target/"
$scpCommand && echo "$scpCommand execute done !"