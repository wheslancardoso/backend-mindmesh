#!/bin/bash

echo "=============================================="
echo " MindMesh - Script de Liberação da Porta 5432"
echo " (Zorin OS / Ubuntu / Debian)"
echo "=============================================="

echo ""
echo "[1/5] Verificando serviços PostgreSQL locais..."
SERVICE=$(systemctl list-unit-files | grep postgresql | awk '{print $1}')

if [ -n "$SERVICE" ]; then
    echo "→ Serviço encontrado: $SERVICE"
    echo "→ Parando serviço..."
    sudo systemctl stop postgresql 2>/dev/null
    sudo systemctl stop postgresql.service 2>/dev/null
    sudo systemctl stop postgresql@14-main 2>/dev/null
else
    echo "→ Nenhum serviço PostgreSQL encontrado."
fi

echo ""
echo "[2/5] Verificando processos usando a porta 5432..."
PID=$(sudo lsof -t -i:5432)

if [ -n "$PID" ]; then
    echo "→ Processo encontrado usando a porta 5432: PID=$PID"
    echo "→ Matando processo..."
    sudo kill -9 $PID
else
    echo "→ Nenhum processo ocupando a porta 5432."
fi

echo ""
echo "[3/5] Validando porta 5432..."
sleep 1
if sudo lsof -i:5432 >/dev/null; then
    echo "❌ ERRO: Porta 5432 ainda está ocupada!"
    echo "Abortando."
    exit 1
else
    echo "✅ Porta 5432 está livre!"
fi

echo ""
echo "[4/5] Subindo Docker Compose do MindMesh..."
docker compose up -d

echo ""
echo "[5/5] Finalizado!"
echo "MindMesh Database iniciado com sucesso."
echo "Acesse via: localhost:5432"
echo ""
