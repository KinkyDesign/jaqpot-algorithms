FROM jboss/wildfly

USER root

EXPOSE 8080
EXPOSE 9990

ADD ./target/jaqpot-algorithms-4.0.2.war /opt/jboss/wildfly/standalone/deployments/

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
