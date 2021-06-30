FROM openjdk:16-jdk-slim

COPY build/libs/SMARollingScraper-*-all.jar /usr/local/lib/SMARollingScraper.jar

RUN mkdir /smars
WORKDIR /smars

ENTRYPOINT ["java", "-Xms2G", "-Xmx2G", "-jar", "/usr/local/lib/SMARollingScraper.jar"]
