package net.misemise.ore_picker.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.misemise.ore_picker.network.HoldC2SPayload;
import net.misemise.ore_picker.network.DestroyedCountS2CPayload;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Client initializer — ラムダ/ローカルキャプチャを避けた実装（完全版）
 *
 * - 匿名クラス / static 内部クラスのみを使う（ラムダ/ローカルキャプチャ問題回避）
 * - ClientTickEvents, ClientPlayNetworking 周りは reflection で互換性を確保
 * - HUD とサーバ送受信は既存のクラスに委譲
 */
public class Ore_pickerClient implements ClientModInitializer {
    private KeyBinding holdKey;
    private boolean lastPressed = false;

    @Override
    public void onInitializeClient() {
        // HUD 登録（存在すれば）
        try {
            OrePickerHud.register();
        } catch (Throwable ignored) {}

        // キーバインドをフィールドで持つ（ローカルにしない）
        holdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orepicker.hold",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.orepicker"
        ));

        // ClientTickEvents 登録（reflection）
        try {
            registerClientTickListenerReflection();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // DestroyedCount 受信登録（reflection）
        try {
            registerDestroyedCountReceiverReflection();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ---------------- ClientTick registration ----------------
    private void registerClientTickListenerReflection() {
        try {
            Class<?> ccte = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
            java.lang.reflect.Field f = null;
            try {
                f = ccte.getField("END_CLIENT_TICK");
            } catch (NoSuchFieldException nsf) {
                for (java.lang.reflect.Field ff : ccte.getFields()) {
                    if (ff.getName().toUpperCase().contains("END")) {
                        f = ff;
                        break;
                    }
                }
            }
            if (f == null) {
                System.err.println("[OrePicker-Client] END_CLIENT_TICK field not found.");
                return;
            }

            Object eventObj = f.get(null);
            if (eventObj == null) {
                System.err.println("[OrePicker-Client] END_CLIENT_TICK instance is null.");
                return;
            }

            Method registerMethod = null;
            Class<?> listenerType = null;
            for (Method m : eventObj.getClass().getMethods()) {
                if (!m.getName().equals("register")) continue;
                if (m.getParameterCount() == 1) {
                    registerMethod = m;
                    listenerType = m.getParameterTypes()[0];
                    break;
                }
            }
            if (registerMethod == null || listenerType == null) {
                System.err.println("[OrePicker-Client] register(listener) method not found on END_CLIENT_TICK");
                return;
            }

            // InvocationHandler を static 内部クラスにしてローカルキャプチャを避ける
            final ClientTickInvocationHandler handler = new ClientTickInvocationHandler(this);

            Object proxy = Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[]{listenerType},
                    handler
            );

            registerMethod.invoke(eventObj, proxy);
            System.out.println("[OrePicker-Client] Registered client tick listener reflectively.");
        } catch (ClassNotFoundException cnf) {
            System.err.println("[OrePicker-Client] ClientTickEvents class not found: " + cnf.getMessage());
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // static にして外側のインスタンス参照のみ保持（final で安全）
    private static class ClientTickInvocationHandler implements InvocationHandler {
        private final Ore_pickerClient outer;

        ClientTickInvocationHandler(Ore_pickerClient outer) {
            this.outer = outer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            try {
                MinecraftClient mcFromArgs = null;
                if (args != null) {
                    for (Object a : args) {
                        if (a instanceof MinecraftClient) {
                            mcFromArgs = (MinecraftClient) a;
                            break;
                        }
                    }
                }
                final MinecraftClient mcFinal = (mcFromArgs != null) ? mcFromArgs : MinecraftClient.getInstance();
                if (mcFinal == null) return null;

                // anonymous Runnable から参照されるのは final な mcFinal（これで capture 問題回避）
                mcFinal.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            outer.handleClientTick(mcFinal);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }
    }

    // 実際の tick ハンドリング（インスタンスメソッド）
    private void handleClientTick(MinecraftClient client) {
        try {
            if (holdKey == null || client.player == null) return;
            boolean pressed = holdKey.isPressed();
            if (pressed != lastPressed) {
                lastPressed = pressed;
                boolean sent = trySendHoldViaReflection(pressed);
                try {
                    client.player.sendMessage(Text.of("Client: send hold=" + pressed + " -> " + (sent ? "SENT" : "NOT_SENT")), false);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ---------------- DestroyedCount receiver registration ----------------
    private void registerDestroyedCountReceiverReflection() {
        try {
            Class<?> cpnc;
            try {
                cpnc = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            } catch (ClassNotFoundException e) {
                cpnc = Class.forName("net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking");
            }

            Method target = null;
            for (Method m : cpnc.getMethods()) {
                if (!m.getName().equals("registerGlobalReceiver")) continue;
                if (m.getParameterCount() != 2) continue;
                target = m;
                break;
            }

            if (target == null) {
                System.err.println("[OrePicker-Client] registerGlobalReceiver not found on ClientPlayNetworking");
                return;
            }

            Class<?> handlerInterface = target.getParameterTypes()[1];

            Object handlerProxy = Proxy.newProxyInstance(
                    handlerInterface.getClassLoader(),
                    new Class<?>[]{handlerInterface},
                    new DestroyedCountInvocationHandler()
            );

            // 優先して TYPE を試す
            try {
                target.invoke(null, DestroyedCountS2CPayload.TYPE, handlerProxy);
                System.out.println("[OrePicker-Client] registerGlobalReceiver(TYPE) invoked.");
                return;
            } catch (InvocationTargetException | IllegalAccessException e) {
                // fallthrough
            }

            // 次に Identifier 版を試す
            try {
                Method idBased = null;
                for (Method m : cpnc.getMethods()) {
                    if (!m.getName().equals("registerGlobalReceiver")) continue;
                    if (m.getParameterCount() == 2 && m.getParameterTypes()[0].getSimpleName().equals("Identifier")) {
                        idBased = m;
                        break;
                    }
                }
                if (idBased != null) {
                    idBased.invoke(null, DestroyedCountS2CPayload.ID, handlerProxy);
                    System.out.println("[OrePicker-Client] registerGlobalReceiver(Identifier) invoked.");
                    return;
                }
            } catch (Throwable ignored) {}

            System.err.println("[OrePicker-Client] Could not register destroyed-count receiver reflectively.");
        } catch (ClassNotFoundException cnf) {
            System.err.println("[OrePicker-Client] ClientPlayNetworking class not found: " + cnf.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // static invocation handler — 匿名クラス内で使う変数はすべて final にして参照
    private static class DestroyedCountInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            try {
                MinecraftClient mcFromArgs = null;
                int count = -1;

                if (args != null) {
                    for (Object a : args) {
                        if (a == null) continue;
                        if (a instanceof MinecraftClient) {
                            mcFromArgs = (MinecraftClient) a;
                        } else if (a instanceof PacketByteBuf) {
                            PacketByteBuf buf = (PacketByteBuf) a;
                            try {
                                count = buf.readVarInt();
                            } catch (Throwable ex) {
                                try { buf.readerIndex(0); } catch (Throwable ignore) {}
                                try { count = buf.readInt(); } catch (Throwable ignore) {}
                            }
                        } else {
                            try {
                                Method mcount = a.getClass().getMethod("count");
                                Object v = mcount.invoke(a);
                                if (v instanceof Integer) count = (Integer) v;
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                }

                final MinecraftClient mcFinal = (mcFromArgs != null) ? mcFromArgs : MinecraftClient.getInstance();
                final int cntFinal = count;
                if (mcFinal != null && cntFinal >= 0) {
                    mcFinal.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (mcFinal.player != null) mcFinal.player.sendMessage(Text.of("Destroyed: " + cntFinal), false);
                                OrePickerHud.onDestroyedCount(cntFinal);
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }
    }

    // ---------------- send Hold -> server (reflection) ----------------
    private boolean trySendHoldViaReflection(boolean pressed) {
        try {
            Class<?> cpnc;
            try {
                cpnc = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            } catch (ClassNotFoundException e) {
                cpnc = Class.forName("net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking");
            }

            boolean didSend = false;
            HoldC2SPayload payload = new HoldC2SPayload(pressed);

            for (Method m : cpnc.getMethods()) {
                if (!m.getName().equals("send")) continue;
                Class<?>[] pts = m.getParameterTypes();

                if (pts.length == 1) {
                    try {
                        m.invoke(null, payload);
                        didSend = true;
                        break;
                    } catch (IllegalArgumentException ia) {
                        // incompatible
                    }
                }

                if (pts.length == 2) {
                    try {
                        Object handler = null;
                        try { handler = MinecraftClient.getInstance().getNetworkHandler(); } catch (Throwable ignore) { handler = null; }
                        if (handler != null && pts[0].isAssignableFrom(handler.getClass())) {
                            m.invoke(null, handler, payload);
                            didSend = true;
                            break;
                        }
                    } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException ignore) {}
                }
            }

            if (!didSend) {
                try {
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeBoolean(pressed);
                    for (Method m : cpnc.getMethods()) {
                        if (!m.getName().equals("send")) continue;
                        Class<?>[] pts = m.getParameterTypes();
                        if (pts.length == 2 && pts[0].getSimpleName().equals("Identifier")
                                && PacketByteBuf.class.isAssignableFrom(pts[1])) {
                            m.invoke(null, HoldC2SPayload.ID, buf);
                            didSend = true;
                            break;
                        }
                    }
                } catch (Throwable ignore) {}
            }

            return didSend;
        } catch (ClassNotFoundException cnf) {
            return false;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }
}
