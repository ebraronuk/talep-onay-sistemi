# Cok asamali derleme: derleme asamasindaki JDK ve Maven onbellegi son imaja tasinmaz.
FROM eclipse-temurin:21-jdk AS derleme

WORKDIR /derleme

# Once yalnizca bagimlilik tanimlari kopyalanir; src degistiginde bu katman onbellekten gelir.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src ./src
RUN ./mvnw -B -ntp clean package -DskipTests

# Calisma asamasi: yalnizca JRE ve uygulama jar'i.
FROM eclipse-temurin:21-jre AS calisma

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system talep \
    && useradd --system --gid talep --home /uygulama talep

WORKDIR /uygulama
COPY --from=derleme /derleme/target/talep-onay-*.jar uygulama.jar
RUN chown -R talep:talep /uygulama

USER talep
EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/uygulama/uygulama.jar"]
