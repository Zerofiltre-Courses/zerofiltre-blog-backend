FROM adoptopenjdk/openjdk11:alpine-jre

ARG JAR_FILE=target/blog.jar

ARG PROFILE=dev

WORKDIR /opt/app

COPY opentelemetry-javaagent.jar /opt/app/opentelemetry-javaagent.jar

COPY ${JAR_FILE} blog.jar


ENV OTEL_SERVICE_NAME=zerofiltre-backend-imphilippesimo-${PROFILE}

COPY entrypoint.sh entrypoint.sh

RUN chmod 755 entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]