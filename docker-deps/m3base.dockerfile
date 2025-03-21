FROM python:3.8.13-bullseye

MAINTAINER Page <page@quicksand.com>

ADD m3pys/* /tmp

RUN pip install -v metricflow==0.111.0 && \
    pip install -v SQLAlchemy==1.4.27 && \
    pip install -v PyMySQL==1.0.2 && \
    apt-get update && cd /tmp && \
    wget https://builds.openlogic.com/downloadJDK/openlogic-openjdk/8u332-b09/openlogic-openjdk-8u332-b09-linux-x64-deb.deb && \
    apt-get --fix-broken -y install /tmp/openlogic-openjdk-8u332-b09-linux-x64-deb.deb  && \
    rm /tmp/openlogic-openjdk-8u332-b09-linux-x64-deb.deb  && \
    rm /usr/local/lib/python3.8/site-packages/metricflow/sql_clients/redshift.py && mv /tmp/redshift.py /usr/local/lib/python3.8/site-packages/metricflow/sql_clients/ && \
    rm /usr/local/lib/python3.8/site-packages/metricflow/dataflow/sql_table.py && mv /tmp/sql_table.py /usr/local/lib/python3.8/site-packages/metricflow/dataflow/ && \
    rm /usr/local/lib/python3.8/site-packages/metricflow/plan_conversion/dataflow_to_execution.py && mv /tmp/dataflow_to_execution.py /usr/local/lib/python3.8/site-packages/metricflow/plan_conversion/ && \
    rm /usr/local/lib/python3.8/site-packages/metricflow/telemetry/handlers/handlers.py && mv /tmp/handlers.py /usr/local/lib/python3.8/site-packages/metricflow/telemetry/handlers/



ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64