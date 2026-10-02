FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:appview:installDist --no-daemon -q

FROM eclipse-temurin:25-jre
COPY --from=build /src/server/appview/build/install/appview /app
EXPOSE 8081
ENTRYPOINT ["/app/bin/appview"]
