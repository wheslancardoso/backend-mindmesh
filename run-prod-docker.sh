#!/usr/bin/env bash
echo "======================================="
echo "🐳 Subindo PostgreSQL + PGVector"
echo "======================================="

docker-compose up -d postgres

echo "Aguardando banco iniciar..."
sleep 5

echo "======================================="
echo "🚀 Iniciando MindMesh - PRODUÇÃO"
echo "======================================="

export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/mindmesh_db"
export SPRING_DATASOURCE_USERNAME="admin"
export SPRING_DATASOURCE_PASSWORD="admin"

if [ -z "$OPENAI_API_KEY" ]; then
  echo "❌ ERRO: Defina OPENAI_API_KEY"
  exit 1
fi

mvn spring-boot:run
