#Usage: ./run_middleware.sh <flightsHost> <carsHost> <roomsHost>

./run_rmi.sh > /dev/null 2>&1
java -Djava.rmi.server.codebase=file:$(pwd)/ Server.RMI.RMIMiddleware $1 $2 $3
