#!/bin/bash
targetHost='page.quicksand.com'
targetDir='/usr/local/dev/sync-wsp/bdt-metric-stitcher'
cleanCommand="sh ${targetDir}/sync-clean.sh"
redeployCommand="sudo docker rm -f metric-stitcher && sudo docker rmi -f metric-stitcher:1.0 && sudo docker compose -f ${targetDir}/docker-compose.yml up metric-stitcher"
mvn clean && mvn package -U -DskipTests=true && echo "package done !" && ssh $targetHost -t $cleanCommand && sh ./sync-transport.sh && ssh $targetHost -t $redeployCommand