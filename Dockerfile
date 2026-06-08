FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-26
WORKDIR /app
COPY build/libs/spredning-1.0.0.jar app.jar
CMD ["java", "-jar", "app.jar"]
