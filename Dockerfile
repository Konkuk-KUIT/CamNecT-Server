FROM gradle:8.7-jdk21 AS builder

WORKDIR /build

COPY build.gradle settings.gradle /build/

RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true

COPY src /build/src

RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar app.jar

ENV TZ=Asia/Seoul

ENTRYPOINT ["java", "-jar", "app.jar"]
