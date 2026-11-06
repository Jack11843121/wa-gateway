# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷贝 pom 以便利用依赖缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:17-jre-jammy
LABEL org.opencontainers.image.title="wa-gateway" \
      org.opencontainers.image.description="Open-source WhatsApp Cloud API Gateway" \
      org.opencontainers.image.source="https://github.com/your-org/wa-gateway"

# 非 root 运行
RUN groupadd -r wagw && useradd -r -g wagw -m wagw
WORKDIR /app
COPY --from=build /build/target/wa-gateway.jar app.jar
USER wagw

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]