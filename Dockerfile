FROM amazoncorretto:23-alpine
COPY ./telegram-bot/build/libs/telegram-bot-all.jar /tmp/server.jar
WORKDIR /tmp
ENTRYPOINT ["java","-jar","server.jar"]