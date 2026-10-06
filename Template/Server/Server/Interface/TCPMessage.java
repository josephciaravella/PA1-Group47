package Server.Interface;

import java.io.Serializable;
import java.util.UUID;

public class TCPMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id = UUID.randomUUID().toString();
    private String methodName;
    private Object[] args;
    private Object result;
    private Exception exception;

    // Constructor for creating a request (called by the Client/Proxy)
    public TCPMessage(String methodName, Object... args) {
        this.id = UUID.randomUUID().toString();
        this.methodName = methodName;
        this.args = args;
    }

    // Constructor for creating a response (called by Server/Middleware)
    public TCPMessage(String id, Object result, Exception exception) {
        this.id = id;
        this.result = result;
        this.exception = exception;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getMethodName() { return methodName; }
    public Object[] getArgs() { return args; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public Exception getException() { return exception; }
    public void setException(Exception exception) { this.exception = exception; }
}