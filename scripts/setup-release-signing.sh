#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="${ROOT_DIR}/keystores/kairo-upload-key.jks"
PROPERTIES_PATH="${ROOT_DIR}/keystore.properties"
KEY_ALIAS="${KAIRO_RELEASE_KEY_ALIAS:-kairo-upload}"

generate_password() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 48 | tr -d '\n'
    elif command -v uuidgen >/dev/null 2>&1; then
        {
            uuidgen
            uuidgen
            uuidgen
        } | tr -d '-\n'
    else
        echo "Neither openssl nor uuidgen is available to generate a password." >&2
        exit 1
    fi
}

if ! command -v keytool >/dev/null 2>&1; then
    echo "keytool is required. Install a JDK, then rerun this script." >&2
    exit 1
fi

if [[ -f "${PROPERTIES_PATH}" || -f "${KEYSTORE_PATH}" ]]; then
    echo "Release signing already appears to be configured."
    echo "Properties: ${PROPERTIES_PATH}"
    echo "Keystore:   ${KEYSTORE_PATH}"
    echo "Leaving existing files untouched."
    exit 0
fi

mkdir -p "$(dirname "${KEYSTORE_PATH}")"

PASSWORD="$(generate_password)"

keytool -genkeypair \
    -v \
    -storetype JKS \
    -keystore "${KEYSTORE_PATH}" \
    -storepass "${PASSWORD}" \
    -keypass "${PASSWORD}" \
    -alias "${KEY_ALIAS}" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=Kairo Upload Key, OU=Release, O=Kairo, L=London, ST=England, C=GB"

cat > "${PROPERTIES_PATH}" <<EOF
storeFile=keystores/kairo-upload-key.jks
storePassword=${PASSWORD}
keyAlias=${KEY_ALIAS}
keyPassword=${PASSWORD}
EOF

chmod 600 "${KEYSTORE_PATH}" "${PROPERTIES_PATH}"

echo "Release signing configured."
echo "Properties: ${PROPERTIES_PATH}"
echo "Keystore:   ${KEYSTORE_PATH}"
echo "Back up both files somewhere private before publishing."
