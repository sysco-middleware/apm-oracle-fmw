FROM docker.elastic.co/logstash/logstash:5.6.2
RUN rm -f /usr/share/logstash/pipeline/logstash.conf
ADD pipeline/ /usr/share/logstash/pipeline/
#ADD config/ /usr/share/logstash/config/