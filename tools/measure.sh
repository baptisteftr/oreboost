#!/bin/zsh
# Mesure l'effet d'une config OreBoost sur un monde neuf à seed fixe.
# Usage : tools/measure.sh <label> <fichier-config.json>
# Génère 32x32 chunks (chunks 484..515 en X et Z) puis lance /oreboost count dessus.
set -u
ROOT="${0:A:h:h}"
RUN="$ROOT/run"
LABEL="$1"
CONFIG="${2:A}"
JAVA="${JAVA:-/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java}"
LOG="$RUN/measure-$LABEL.log"

cd "$RUN" || exit 1
rm -rf world in.fifo
cp "$CONFIG" config/oreboost.json || exit 1
mkfifo in.fifo
"$JAVA" -Xmx6G -jar fabric-server-launch.jar nogui < in.fifo > "$LOG" 2>&1 &
PID=$!
exec 3> in.fifo

wait_for() { # motif, timeout en secondes
	local i=0
	while ! grep -q "$1" "$LOG"; do
		kill -0 $PID 2>/dev/null || { echo "serveur arrêté avant '$1'"; return 1; }
		(( i++ >= $2 )) && { echo "timeout en attendant '$1'"; return 1; }
		sleep 1
	done
}

if wait_for 'Done (' 600; then
	echo "oreboost status" >&3
	echo "forceload add 7744 7744 7999 7999" >&3
	echo "forceload add 8000 7744 8255 7999" >&3
	echo "forceload add 7744 8000 7999 8255" >&3
	echo "forceload add 8000 8000 8255 8255" >&3
	for i in {1..120}; do
		sleep 5
		echo "execute positioned 8008 0 8008 run oreboost count 16" >&3
		sleep 2
		grep -q 'OreBoost count : 1089 chunk' "$LOG" && break
		grep -q 'Commande de debug' "$LOG" && break
		kill -0 $PID 2>/dev/null || break
	done
fi
echo "stop" >&3
exec 3>&-
wait $PID
rm -f in.fifo
grep -q 'OreBoost count : 1089 chunk' "$LOG" && echo "OK $LABEL" || echo "ECHEC $LABEL (voir $LOG)"
