#!/bin/bash
# UOP Agent Installer
# Usage: curl -fsSL https://example.com/install-agent.sh | sh -s -- --platform grpc://host:9090 --token <registration-token>

set -e

PLATFORM_URL=""
REGISTRATION_TOKEN=""
INSTALL_DIR="/usr/local/bin"
CONFIG_DIR="/etc/uop-agent"
AGENT_USER="uop-agent"
AGENT_VERSION="0.1.0"
DOWNLOAD_BASE="https://github.com/yourorg/uop/releases/download/v${AGENT_VERSION}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]
Options:
    --platform <url>        Platform gRPC address (e.g., grpc://host:9090)
    --token <token>         Registration token (required for first run)
    --install-dir <path>    Installation directory (default: /usr/local/bin)
    --config-dir <path>     Config directory (default: /etc/uop-agent)
    --user <user>           Service user (default: uop-agent)
    --version <ver>         Agent version to install
    -h, --help              Show this help
EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --platform) PLATFORM_URL="$2"; shift 2 ;;
        --token) REGISTRATION_TOKEN="$2"; shift 2 ;;
        --install-dir) INSTALL_DIR="$2"; shift 2 ;;
        --config-dir) CONFIG_DIR="$2"; shift 2 ;;
        --user) AGENT_USER="$2"; shift 2 ;;
        --version) AGENT_VERSION="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) log_error "Unknown option: $1"; usage; exit 1 ;;
    esac
done

if [[ -z "$PLATFORM_URL" ]]; then
    log_error "Platform URL is required (--platform)"
    usage
    exit 1
fi

# Detect OS
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case $ARCH in
    x86_64) ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
    *) log_error "Unsupported architecture: $ARCH"; exit 1 ;;
esac

log_info "Installing UOP Agent ${AGENT_VERSION} for ${OS}/${ARCH}"

# Check if running as root for system install
if [[ $EUID -ne 0 ]]; then
    log_error "This installer must be run as root (use sudo)"
    exit 1
fi

# Create agent user
if ! id "$AGENT_USER" &>/dev/null; then
    log_info "Creating user: $AGENT_USER"
    useradd -r -s /bin/false -d /var/lib/uop-agent "$AGENT_USER"
fi

# Create directories
mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" "/var/lib/uop-agent" "/var/log/uop-agent"
chown -R "$AGENT_USER:$AGENT_USER" "/var/lib/uop-agent" "/var/log/uop-agent"

# Download agent JAR
JAR_NAME="uop-agent-${AGENT_VERSION}.jar"
JAR_URL="${DOWNLOAD_BASE}/${JAR_NAME}"
JAR_PATH="${INSTALL_DIR}/${JAR_NAME}"

log_info "Downloading agent from ${JAR_URL}"
curl -fsSL "$JAR_URL" -o "$JAR_PATH" || {
    log_error "Failed to download agent JAR"
    exit 1
}
chmod 644 "$JAR_PATH"

# Create symlink
ln -sf "$JAR_PATH" "${INSTALL_DIR}/uop-agent"

# Create config file
cat > "${CONFIG_DIR}/agent.yaml" <<EOF
platform:
  url: "${PLATFORM_URL}"
agent:
  credential_path: "/var/lib/uop-agent/credentials"
EOF
chmod 640 "${CONFIG_DIR}/agent.yaml"
chown "$AGENT_USER:$AGENT_USER" "${CONFIG_DIR}/agent.yaml"

# Create systemd service
cat > /etc/systemd/system/uop-agent.service <<EOF
[Unit]
Description=UOP Monitoring Agent
After=network.target

[Service]
Type=simple
User=${AGENT_USER}
Group=${AGENT_USER}
ExecStart=${INSTALL_DIR}/uop-agent --platform ${PLATFORM_URL} ${REGISTRATION_TOKEN:+--token ${REGISTRATION_TOKEN}} --config ${CONFIG_DIR}/agent.yaml
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=uop-agent

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/uop-agent /var/log/uop-agent

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd and enable
systemctl daemon-reload
systemctl enable uop-agent

log_info "Installation complete!"
log_info "Start agent with: systemctl start uop-agent"
log_info "View logs with: journalctl -u uop-agent -f"