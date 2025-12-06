#!/usr/bin/env bash
echo "======================================="
echo "🚀 Iniciando MindMesh - MODO LOCAL"
echo "======================================="

# Ativa o profile local
export SPRING_PROFILES_ACTIVE=local

# Desabilita chamadas reais para OpenAI
export OPENAI_API_KEY=""

# Limpa o target antes de rodar (opcional)
# mvn clean

# Roda a aplicação
mvn spring-boot:run
