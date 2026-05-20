package de.craftplay.plotextras.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class FloodgateHook {

    private final JavaPlugin plugin;
    private boolean warned;

    public FloodgateHook(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate")
                || Bukkit.getPluginManager().isPluginEnabled("Floodgate");
    }

    public boolean isBedrockPlayer(final Player player) {
        if (!isAvailable()) {
            return false;
        }
        final Object api = floodgateApi();
        if (api == null) {
            return false;
        }
        final Object result = invoke(api, "isFloodgatePlayer", UUID.class, player.getUniqueId());
        return result instanceof Boolean && (Boolean) result;
    }

    public boolean sendSimpleForm(
            final Player player,
            final String title,
            final String content,
            final List<String> buttons,
            final Consumer<Integer> responseHandler
    ) {
        final Object api = floodgateApi();
        if (api == null) {
            return false;
        }

        try {
            final Class<?> simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            final Object builder = simpleFormClass.getMethod("builder").invoke(null);
            invokeBuilder(builder, "title", title);
            invokeBuilder(builder, "content", content == null ? "" : content);
            for (final String button : buttons) {
                invokeBuilder(builder, "button", button);
            }
            registerResultHandler(builder, responseHandler);
            final Object form = builder.getClass().getMethod("build").invoke(builder);
            return sendForm(api, player.getUniqueId(), form);
        } catch (final ClassNotFoundException exception) {
            warn("Cumulus/Floodgate-Forms wurden nicht gefunden. Bedrock-Forms sind deaktiviert.", exception);
            return false;
        } catch (final ReflectiveOperationException exception) {
            warn("Bedrock-Form konnte nicht erstellt werden.", exception);
            return false;
        }
    }

    private Object floodgateApi() {
        try {
            final Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return apiClass.getMethod("getInstance").invoke(null);
        } catch (final ClassNotFoundException | NoSuchMethodException exception) {
            return null;
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warn("FloodgateApi konnte nicht gelesen werden.", exception);
            return null;
        }
    }

    private void registerResultHandler(final Object builder, final Consumer<Integer> responseHandler)
            throws ReflectiveOperationException {
        for (final Method method : builder.getClass().getMethods()) {
            if (!"validResultHandler".equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            final Class<?> parameterType = method.getParameterTypes()[0];
            if (Consumer.class.isAssignableFrom(parameterType)) {
                final Consumer<Object> consumer = response -> responseHandler.accept(clickedButtonId(response));
                method.invoke(builder, consumer);
                return;
            }
            if (BiConsumer.class.isAssignableFrom(parameterType)) {
                final BiConsumer<Object, Object> consumer = (form, response) -> responseHandler.accept(clickedButtonId(response));
                method.invoke(builder, consumer);
                return;
            }
        }
        throw new NoSuchMethodException("validResultHandler");
    }

    private int clickedButtonId(final Object response) {
        final Object modern = invokeNoArgs(response, "clickedButtonId");
        if (modern instanceof Number) {
            return ((Number) modern).intValue();
        }
        final Object legacy = invokeNoArgs(response, "getClickedButtonId");
        if (legacy instanceof Number) {
            return ((Number) legacy).intValue();
        }
        return -1;
    }

    private boolean sendForm(final Object api, final UUID uuid, final Object form)
            throws ReflectiveOperationException {
        for (final Method method : api.getClass().getMethods()) {
            if (!"sendForm".equals(method.getName()) || method.getParameterTypes().length != 2) {
                continue;
            }
            if (!UUID.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            final Object result = method.invoke(api, uuid, form);
            return !(result instanceof Boolean) || (Boolean) result;
        }
        return false;
    }

    private void invokeBuilder(final Object builder, final String methodName, final String value)
            throws ReflectiveOperationException {
        final Method method = builder.getClass().getMethod(methodName, String.class);
        method.invoke(builder, value);
    }

    private Object invoke(final Object target, final String methodName, final Class<?> parameterType, final Object argument) {
        try {
            final Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (final ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object invokeNoArgs(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (final ReflectiveOperationException exception) {
            return null;
        }
    }

    private void warn(final String message, final Exception exception) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().log(Level.WARNING, message, exception);
    }
}
