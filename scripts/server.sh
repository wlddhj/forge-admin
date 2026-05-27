#!/bin/bash
# forge-server 后台服务管理脚本
# 用法: ./scripts/server.sh {start|stop|restart|status|logs}

SERVER_DIR="$(cd "$(dirname "$0")/../apps/forge-server" && pwd)"
PID_FILE="$SERVER_DIR/.server.pid"
LOG_FILE="/tmp/forge-server.log"
PORT=8181

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

get_pid() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return 0
        fi
        rm -f "$PID_FILE"
    fi
    # Fallback: find by port
    lsof -i :$PORT -t 2>/dev/null | head -1
}

is_running() {
    local pid=$(get_pid)
    [ -n "$pid" ]
}

wait_for_port() {
    local timeout=${1:-60}
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if curl -s -o /dev/null -w "%{http_code}" http://localhost:$PORT/api/auth/captcha 2>/dev/null | grep -q "200\|500"; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

do_start() {
    if is_running; then
        local pid=$(get_pid)
        echo -e "${YELLOW}服务已在运行中 (PID: $pid)${NC}"
        return 1
    fi

    echo -n "正在启动后端服务..."
    cd "$SERVER_DIR"
    nohup mvn spring-boot:run -Dmaven.test.skip=true > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"

    if wait_for_port 90; then
        local pid=$(get_pid)
        echo -e "${GREEN}启动成功 (PID: $pid, 端口: $PORT)${NC}"
        echo "日志文件: $LOG_FILE"
    else
        echo -e "${RED}启动超时，请检查日志: $LOG_FILE${NC}"
        return 1
    fi
}

do_stop() {
    if ! is_running; then
        echo -e "${YELLOW}服务未运行${NC}"
        return 0
    fi

    local pid=$(get_pid)
    echo -n "正在停止服务 (PID: $pid)..."
    kill "$pid" 2>/dev/null

    local elapsed=0
    while [ $elapsed -lt 15 ]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo -e "${GREEN}已停止${NC}"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    echo -n "强制终止..."
    kill -9 "$pid" 2>/dev/null
    rm -f "$PID_FILE"
    echo -e "${GREEN}已终止${NC}"
}

do_status() {
    if is_running; then
        local pid=$(get_pid)
        echo -e "${GREEN}服务运行中${NC}  PID: $pid  端口: $PORT"
        echo "日志: $LOG_FILE"
    else
        echo -e "${RED}服务未运行${NC}"
    fi
}

do_logs() {
    if [ ! -f "$LOG_FILE" ]; then
        echo "日志文件不存在: $LOG_FILE"
        return 1
    fi

    local lines=${1:-100}
    echo "最近 $lines 行日志 ($LOG_FILE):"
    echo "---"
    tail -"$lines" "$LOG_FILE"
}

case "$1" in
    start)   do_start ;;
    stop)    do_stop ;;
    restart) do_stop; sleep 2; do_start ;;
    status)  do_status ;;
    logs)    do_logs "${2:-100}" ;;
    *)
        echo "forge-server 后台服务管理"
        echo ""
        echo "用法: $0 {start|stop|restart|status|logs [行数]}"
        echo ""
        echo "  start    启动服务"
        echo "  stop     停止服务"
        echo "  restart  重启服务"
        echo "  status   查看运行状态"
        echo "  logs     查看日志 (默认100行)"
        exit 1
        ;;
esac
