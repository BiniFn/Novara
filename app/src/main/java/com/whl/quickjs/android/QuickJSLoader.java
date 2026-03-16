package com.whl.quickjs.android;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

public final class QuickJSLoader {
    private static volatile boolean initialized;

    private QuickJSLoader() {
    }

    public interface Console {
    }

    public static void init() {
        init(Boolean.FALSE);
    }

    public static void init(Boolean redirectStdout) {
        if (!initialized) {
            synchronized (QuickJSLoader.class) {
                if (!initialized) {
                    initialized = tryLoad("quickjs-android-wrapper") || tryLoad("quickjs");
                }
            }
        }
    }

    public static void startRedirectingStdoutStderr(String prefix) {
    }

    private static boolean tryLoad(String libraryName) {
        try {
            System.loadLibrary(libraryName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object oOoOoOoOoOoOoO0o(Console console, Object[] args) {
        if (console == null) {
            return null;
        }
        Method[] methods = console.getClass().getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 0) {
                    return method.invoke(console);
                }
                if (parameterTypes.length == 1 && parameterTypes[0].isArray()) {
                    Object arrayArg = args == null ? Array.newInstance(parameterTypes[0].getComponentType(), 0) : args;
                    return method.invoke(console, arrayArg);
                }
                if (method.isVarArgs()) {
                    Object[] invokeArgs = new Object[parameterTypes.length];
                    if (parameterTypes.length > 0) {
                        Class<?> componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
                        int fixedCount = parameterTypes.length - 1;
                        for (int index = 0; index < fixedCount; index++) {
                            invokeArgs[index] = args != null && index < args.length ? args[index] : null;
                        }
                        int varArgCount = Math.max(0, (args == null ? 0 : args.length) - fixedCount);
                        Object varArgArray = Array.newInstance(componentType, varArgCount);
                        for (int index = 0; index < varArgCount; index++) {
                            Array.set(varArgArray, index, args[fixedCount + index]);
                        }
                        invokeArgs[parameterTypes.length - 1] = varArgArray;
                    }
                    return method.invoke(console, invokeArgs);
                }
                if (parameterTypes.length == (args == null ? 0 : args.length)) {
                    return method.invoke(console, args);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
