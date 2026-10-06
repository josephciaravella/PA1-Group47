package Server.Common;

import Server.Interface.TCPMessage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GenericDispatcher {

    /**
     * Finds and executes the method requested in 'message' on the 'target' object.
     */
    public static Object dispatch(Object target, TCPMessage message) throws Exception {
        // looks for method name
        String methodName = message.getMethodName();
        // looks for method arguments
        Object[] args = message.getArgs();
        if (args == null) {
            args = new Object[0];
        }

        // gets all methods from the target class
        Method[] methods = target.getClass().getMethods();
        Method targetMethod = null;

        for (Method m : methods) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] paramTypes = m.getParameterTypes();
            if (paramTypes.length != args.length) {
                continue;
            }

            // Check if arguments match parameter types
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !isCompatible(paramTypes[i], args[i].getClass())) {
                    match = false;
                    break;
                }
            }

            if (match) {
                targetMethod = m;
                break;
            }
        }

        if (targetMethod == null) {
            throw new NoSuchMethodException("No matching method found for: " + methodName);
        }

        try {
            // Dynamically invoke the method
            return targetMethod.invoke(target, args);
        } catch (InvocationTargetException ite) {
            // Unwrap the actual underlying exception thrown by the method
            Throwable cause = ite.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new Exception(cause);
        }
    }

    /**
     * Checks if an argument type can be passed to a parameter type,
     * accounting for Java's primitive boxing (e.g. int vs Integer).
     */
    private static boolean isCompatible(Class<?> paramType, Class<?> argType) {
        if (paramType.isAssignableFrom(argType)) {
            return true;
        }
        if (paramType == int.class && argType == Integer.class) return true;
        if (paramType == boolean.class && argType == Boolean.class) return true;
        if (paramType == long.class && argType == Long.class) return true;
        if (paramType == double.class && argType == Double.class) return true;
        if (paramType == float.class && argType == Float.class) return true;

        return false;
    }
}
