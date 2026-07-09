package cc.wechat.observatory.wechat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class WechatImageDownloadCallbackFactory {
    interface Handler {
        void onSceneEnd(Object callback, Object[] args);
    }

    Object create(
            ClassLoader classLoader,
            Class<?> progressCallbackClass,
            Class<?> sceneEndCallbackClass,
            final Handler handler) {
        return Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{progressCallbackClass, sceneEndCallbackClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method == null ? "" : method.getName();
                        if ("toString".equals(name)) {
                            return "WechatObservatoryImageDownloadCallback";
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
                        }
                        if ("onSceneEnd".equals(name)) {
                            if (handler != null) {
                                handler.onSceneEnd(proxy, args);
                            }
                            return null;
                        }
                        return null;
                    }
                });
    }
}
