package com.synarsis.airtacticalarsenal.client.fx.light;

import java.lang.reflect.Method;

public final class ShaderPackGuard {

    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.apiimpl.IrisApiV0Impl"};

    private static boolean looked;
    private static Object api;
    private static Method inUse;

    private ShaderPackGuard() {
    }

    public static boolean shaderPackInUse() {
        if (!looked) {
            looked = true;
            for (String name : API_CLASSES) {
                try {
                    Class<?> type = Class.forName(name);
                    Object instance = type.getMethod("getInstance").invoke(null);
                    Method method = type.getMethod("isShaderPackInUse");
                    api = instance;
                    inUse = method;
                    break;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                }
            }
        }
        if (inUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
