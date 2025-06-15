FROM adoptopenjdk/openjdk11:alpine-jre

WORKDIR /opt/app

COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar

COPY target/blog.jar blog.jar


# Define env variables to configure the
#OTEL_SERVICE_NAME, OTEL_METRICS_EXPORTER, OTEL_EXPORTER_OTLP_PROTOCOL,OTEL_EXPORTER_OTLP_ENDPOINT

ENTRYPOINT ["java", "-Xms1g", "-Xmx4g", "-XX:+ExitOnOutOfMemoryError","-javaagent:opentelemetry-javaagent.jar", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "blog.jar"]