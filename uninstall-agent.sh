#!/bin/bash
# UOP Agent Uninstaller
# Usage: sudo ./uninstall-agent.sh

set -e

AGENT_USER="uop-agent"
INSTALL_DIR="/usr/local/bin"
CONFIG_DIR="/etc/uop-agent"
DATA_DIR="/var/lib/uop-agent"
LOG_DIR="/var/log/uop-agent"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

if [[ $EUID -ne 0 ]]; then
    log_error "This uninstaller must be run as root (use sudo)"
    exit 1
fi

log_info "Uninstalling UOP Agent..."

# Stop and disable service
if systemctl is-active --quiet uop-agent 2>/dev/null; then
    log_info "Stopping uop-agent service..."
    systemctl stop uop-agent
fi

if systemctl is-enabled --quiet uop-agent 2>/dev/null; then
    log_info "Disabling uop-agent service..."
    systemctl disable uop-agent
fi

# Remove systemd service file
if [[ -f /etc/systemd/system/uop-agent.service ]]; then
    log_info "Removing systemd service..."
    rm -f /etc/systemd/system/uop-agent.service
    systemctl daemon-reload
fi

# Remove binary and symlink
if [[ -f "${INSTALL_DIR}/uop-agent" ]]; then
    log_info "Removing agent binary..."
    rm -f "${INSTALL_DIR}/uop-agent"
fi

if [[ -f "${INSTALL_DIR}/uop-agent-"*.jar ]]; then
    log_info "Removing agent JAR..."
    rm -f "${INSTALL_DIR}/uop-agent-"*.jar
fi

# Remove config
if [[ -d "${CONFIG_DIR}" ]]; then
    log_info "Removing config directory..."
    rm -rf "${CONFIG_DIR}"
fi

# Remove data directory (credentials, etc.)
if [[ -d "${DATA_DIR}" ]]; then
    log_warn "Removing data directory (including credentials)..."
    rm -rf "${DATA_DIR}"
fi

# Remove log directory
if [[ -d "${LOG_DIR}" ]]; then
    log_info "Removing log directory..."
    rm -rf "${LOG_DIR}"
fi

# Remove user
if id "$AGENT_USER" &>/dev/null; then
    log_info "Removing user: $AGENT_USER"
    userdel "$AGENT_USER" 2>/dev/null || log_warn "Could not remove user $AGENT_USER"
fi

log_info "Uninstallation complete!"