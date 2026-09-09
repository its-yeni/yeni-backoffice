FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew && ./gradlew clean :api:bootJar -x test

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/api/build/libs/*.jar app.jar

# 512MB 머신에 맞춘 JVM 예산: 힙 ~256m + 메타스페이스 144m + 코드캐시 40m + 다이렉트 16m
# → RSS ~490MB 목표. 힙 OOM 시 스래싱 대신 즉시 종료(fly가 깨끗하게 재시작).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -XX:MaxMetaspaceSize=144m -XX:ReservedCodeCacheSize=40m -XX:MaxDirectMemorySize=16m -Xss384k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE="fly"
ENV SERVER_TOMCAT_THREADS_MAX="20"
ENV SERVER_TOMCAT_ACCEPT_COUNT="25"
ENV SPRING_JMX_ENABLED="false"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
