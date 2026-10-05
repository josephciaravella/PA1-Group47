#!/bin/bash
# Usage: ./run_server_tcp.sh [<serverName> [<port>]]

java -cp . Server.TCP.TCPResourceManager $1 $2
