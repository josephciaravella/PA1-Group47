#!/bin/bash
# Usage: ./run_middleware_tcp.sh [<flightsHost[:port]> <carsHost[:port]> <roomsHost[:port]> [middlewarePort]]

java -cp . Server.TCP.TCPMiddleware $1 $2 $3 $4
