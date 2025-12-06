# Build da aplicação
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copia os arquivos de build
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline

COPY src ./src

# Build do jar (sem testes pra ficar mais rápido)
RUN mvn -q -e -B clean package -DskipTests

# Imagem final de runtime
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copia o jar da fase de build
# Confere o nome do jar em target/ (ajusta se for diferente)
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Porta padrão do Spring Boot
EXPOSE 8080

# Usa as variáveis de ambiente injetadas pelo Docker
ENTRYPOINT ["java","-jar","app.jar"]
