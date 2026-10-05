#!/bin/bash 

# TODO: SPECIFY THE HOSTNAMES OF 4 CS MACHINES (tr-open-01, tr-open-02, etc...)
# For local testing, you can leave it empty or use: MACHINES=(localhost localhost localhost localhost)
MACHINES=( "tr-open-01", "tr-open-02", "tr-open-03", "tr-open-04" )

PORT_FLIGHTS=50001
PORT_CARS=50002
PORT_ROOMS=50003
PORT_MW=50000

# Default to localhost if no machines specified
if [ ${#MACHINES[@]} -eq 0 ]; then
	MACHINES=(localhost localhost localhost localhost)
fi

# Build commands depending on whether running locally or via SSH
build_cmd() {
	local host=$1
	local cmd=$2
	if [ "$host" = "localhost" ] || [ "$host" = "127.0.0.1" ]; then
		echo "cd $(pwd) > /dev/null; echo -n 'Running on '; hostname; $cmd"
	else
		echo "ssh -t $host \"cd $(pwd) > /dev/null; echo -n 'Connected to '; hostname; $cmd\""
	fi
}

CMD_FLIGHTS=$(build_cmd "${MACHINES[0]}" "./run_server_tcp.sh Flights ${PORT_FLIGHTS}")
CMD_CARS=$(build_cmd "${MACHINES[1]}" "./run_server_tcp.sh Cars ${PORT_CARS}")
CMD_ROOMS=$(build_cmd "${MACHINES[2]}" "./run_server_tcp.sh Rooms ${PORT_ROOMS}")
CMD_MW=$(build_cmd "${MACHINES[3]}" "sleep 1s; ./run_middleware_tcp.sh ${MACHINES[0]}:${PORT_FLIGHTS} ${MACHINES[1]}:${PORT_CARS} ${MACHINES[2]}:${PORT_ROOMS} ${PORT_MW}")

tmux new-session \; \
	split-window -h \; \
	split-window -v \; \
	split-window -v \; \
	select-layout main-vertical \; \
	select-pane -t 1 \; \
	send-keys "$CMD_FLIGHTS" C-m \; \
	select-pane -t 2 \; \
	send-keys "$CMD_CARS" C-m \; \
	select-pane -t 3 \; \
	send-keys "$CMD_ROOMS" C-m \; \
	select-pane -t 0 \; \
	send-keys "$CMD_MW" C-m \;
