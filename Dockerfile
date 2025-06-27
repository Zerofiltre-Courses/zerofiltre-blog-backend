FROM adoptopenjdk/openjdk11:alpine-jre

WORKDIR /opt/app

COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar

COPY target/blog.jar blog.jar


ENV OTEL_SERVICE_NAME=zerofiltre-blog-api-k8slive\
    OTEL_EXPORTER_OTLP_PROTOCOL=grpc \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otelcol-opentelemetry-collector.zerofiltre-bootcamp.svc.cluster.local:4317

ENTRYPOINT ["java", "-Xms1g", "-Xmx4g", "-XX:+ExitOnOutOfMemoryError","-javaagent:opentelemetry-javaagent.jar", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "blog.jar"]