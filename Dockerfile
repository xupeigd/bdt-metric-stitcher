FROM m3base:1.111

MAINTAINER Page <page@quicksand.com>

ADD ./target/bdt-metric-stitcher.jar /bdt-metric-stitcher.jar

ENTRYPOINT ["nohup","java","-jar","-agentlib:jdwp=transport=dt_socket,address=8799,server=y,suspend=n","/bdt-metric-stitcher.jar","&"]