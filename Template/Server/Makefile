all: compile-server-all

RMIInterface.jar: Server/Interface/*.java
	@echo "Compiling server interface"
	javac Server/Interface/*.java
	jar cvf RMIInterface.jar Server/Interface/*.class

compile-server-all: Server/RMI/*.java Server/Interface/*.java Server/Common/*.java Server/TCP/*.java
	javac Server/RMI/*.java Server/Interface/*.java Server/Common/*.java Server/TCP/*.java

clean:
	rm -f Server/Interface/*.class Server/Common/*.class Server/RMI/*.class Server/TCP/*.class
	rm -f RMIInterface.jar

