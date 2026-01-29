FROM amazoncorretto:23-alpine
COPY ./sources/server/build/libs/server-all.jar /tmp/server.jar
WORKDIR /tmp
ENTRYPOINT ["java","-jar","server.jar"]