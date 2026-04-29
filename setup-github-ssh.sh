#!/usr/bin/env bash
# setup-github-ssh.sh
# Настройка SSH-доступа к GitHub с использованием приватного ключа из переменной окружения SSH_KEY

set -euo pipefail

readonly SSH_DIR="$HOME/.ssh"
readonly KEY_FILE="$SSH_DIR/github_qwen"
readonly SSH_CONFIG="$SSH_DIR/config"
readonly GITHUB_HOST="github.com"

log() { echo "🔐 [SSH-SETUP] $*"; }
error() { echo "❌ [ERROR] $*" >&2; exit 1; }

# Проверка наличия ключа
if [[ -z "${SSH_KEY:-}" ]]; then
    error "Переменная окружения SSH_KEY не установлена"
fi

# Создание ~/.ssh с безопасными правами
mkdir -p "$SSH_DIR"
chmod 700 "$SSH_DIR"

# Запись приватного ключа
log "Запись приватного ключа..."
# Обработка ключа: поддержка форматов с/без явных заголовков
echo "$SSH_KEY" | sed -e 's/\\n/\n/g' > "$KEY_FILE"
chmod 600 "$KEY_FILE"

# Генерация публичного ключа (для отладки/верификации)
if command -v ssh-keygen &>/dev/null && ! [[ -f "${KEY_FILE}.pub" ]]; then
    ssh-keygen -y -f "$KEY_FILE" > "${KEY_FILE}.pub" 2>/dev/null || true
fi

# Настройка SSH config для GitHub
log "Настройка ~/.ssh/config..."
cat >> "$SSH_CONFIG" << EOF

# Auto-generated: GitHub SSH config for qwen-coder
Host $GITHUB_HOST
    HostName $GITHUB_HOST
    User git
    IdentityFile $KEY_FILE
    IdentitiesOnly yes
    AddKeysToAgent yes
    StrictHostKeyChecking accept-new
EOF
chmod 600 "$SSH_CONFIG"

# Запуск ssh-agent и добавление ключа
if command -v ssh-agent &>/dev/null; then
    log "Запуск ssh-agent..."
    eval "$(ssh-agent -s)" >/dev/null 2>&1 || true
    ssh-add "$KEY_FILE" 2>/dev/null || log "Предупреждение: не удалось добавить ключ в ssh-agent"
fi

# Проверка подключения (опционально, можно отключить в CI)
if [[ "${TEST_CONNECTION:-true}" == "true" ]]; then
    log "Проверка подключения к GitHub..."
    if ssh -T -o ConnectTimeout=10 git@"$GITHUB_HOST" 2>&1 | grep -q "successfully authenticated"; then
        log "✅ Успешная аутентификация на GitHub"
    else
        log "⚠️ Не удалось проверить подключение (это нормально в CI без TTY)"
    fi
fi

# Опциональная очистка: удаление ключа с диска после добавления в agent
if [[ "${CLEANUP_KEY_FILE:-false}" == "true" ]]; then
    log "Очистка файла ключа с диска..."
    shred -u "$KEY_FILE" 2>/dev/null || rm -f "$KEY_FILE"
    rm -f "${KEY_FILE}.pub"
fi

log "Готово! SSH настроен для работы с GitHub"
