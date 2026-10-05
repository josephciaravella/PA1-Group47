#!/bin/bash
# Usage: ./run_client_tcp.sh [<server_hostname> [<server_port>]]

java -cp ../Server/RMIInterface.jar:. Client.TCPClient $1 $2
