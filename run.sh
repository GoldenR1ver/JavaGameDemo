#!/usr/bin/env bash
# ============================================================
#  Hulu Battle - single launcher for macOS / Linux
#
#  Interactive:   ./run.sh
#  Direct usage:  ./run.sh server [port]
#                 ./run.sh client [host] [port]
#                 ./run.sh replay [file]
#                 ./run.sh test
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

PORT="${PORT:-7776}"
MAIN="com.gameengine.example.Game"
REPLAY="com.gameengine.example.ReplayLauncher"
SEP=":"

build() {
  if [ ! -d target/classes ]; then
    echo "  [build] first run, resolving dependencies..."
    mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
  else
    mvn -q compile
    [ -f target/classpath.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
  fi
  CP="target/classes${SEP}$(cat target/classpath.txt)"
}

require_maven() {
  command -v mvn >/dev/null 2>&1 || {
    echo "[ERROR] Maven was not found in PATH. Install Maven 3.8+ and a JDK 11+."
    exit 1
  }
}

run_server() {
  local p="${1:-$PORT}"
  build
  echo "  Hosting on port $p. Press Ctrl+C to stop."
  java -cp "$CP" "$MAIN" server 0.0.0.0 "$p"
}

run_client() {
  local host="${1:-127.0.0.1}"
  local p="${2:-$PORT}"
  build
  java -cp "$CP" "$MAIN" client "$host" "$p"
}

run_replay() {
  build
  if [ -n "${1:-}" ]; then
    java -cp "$CP" "$REPLAY" "$1"
  else
    java -cp "$CP" "$REPLAY"
  fi
}

run_tests() {
  mvn test
}

menu() {
  clear || true
  cat <<'BANNER'

  ==============================================
     HULU BATTLE  -  launcher
  ==============================================
     1  Single player
     2  Host a server
     3  Join a server
     4  Host + join   (two windows, local demo)
     5  Replay latest match
     6  Run tests
     0  Exit
  ==============================================

BANNER
  printf "  choose > "
  read -r choice
  case "$choice" in
    1) build; java -cp "$CP" "$MAIN";;
    2) run_server;;
    3) printf "  server address [127.0.0.1]: "; read -r host; run_client "${host:-127.0.0.1}";;
    4)
      build
      if command -v x-terminal-emulator >/dev/null 2>&1; then
        x-terminal-emulator -e bash -c "cd '$(pwd)'; java -cp '$CP' '$MAIN' server 0.0.0.0 '$PORT'"
      elif command -v gnome-terminal >/dev/null 2>&1; then
        gnome-terminal -- bash -c "cd '$(pwd)'; java -cp '$CP' '$MAIN' server 0.0.0.0 '$PORT'"
      elif command -v open >/dev/null 2>&1; then
        osascript -e "tell application \"Terminal\" to do script \"cd '$(pwd)'; java -cp '$CP' '$MAIN' server 0.0.0.0 '$PORT'\""
      else
        echo "  No terminal emulator found, starting server in this window."
        java -cp "$CP" "$MAIN" server 0.0.0.0 "$PORT"
        return
      fi
      sleep 4
      java -cp "$CP" "$MAIN" client 127.0.0.1 "$PORT";;
    5) run_replay;;
    6) run_tests;;
    0) exit 0;;
    *) ;;
  esac
  echo
  read -r -p "  press Enter to return to the menu..." _ || true
  menu
}

case "${1:-}" in
  server) shift; run_server "${1:-$PORT}";;
  client) shift; run_client "${1:-127.0.0.1}" "${2:-$PORT}";;
  replay) shift; run_replay "${1:-}";;
  test)   run_tests;;
  *)      require_maven; menu;;
esac
