package de.craftplay.plotextras.integration;

import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public final class BedrockService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private boolean floodgateAvailable;
    private boolean formsAvailable;
    private Object floodgateApi;
    private Method isFloodgatePlayerMethod;
    private Method sendFormMethod;
    private Class<?> simpleFormClass;

    public BedrockService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
    }

    public void reload() {
        floodgateAvailable = false;
        formsAvailable = false;
        floodgateApi = null;
        isFloodgatePlayerMethod = null;
        sendFormMethod = null;
        simpleFormClass = null;
        if (!featureToggleService.isEnabled("integrations.floodgate")
                || !plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
            return;
        }

        try {
            final Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            sendFormMethod = findSendFormMethod(apiClass);
            simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            formsAvailable = sendFormMethod != null && simpleFormClass.getMethod("builder") != null;
            floodgateAvailable = true;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Floodgate wurde gefunden, konnte aber nicht angebunden werden.", exception);
        }
    }

    public boolean isBedrockPlayer(final Player player) {
        if (!floodgateAvailable || player == null || isFloodgatePlayerMethod == null || floodgateApi == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isFloodgatePlayerMethod.invoke(floodgateApi, player.getUniqueId()));
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Bedrock-Status konnte nicht ueber Floodgate gelesen werden.", exception);
            return false;
        }
    }

    public boolean canUseForms(final Player player) {
        return formsAvailable
                && featureToggleService.isEnabled("player.gui.bedrock.forms")
                && isBedrockPlayer(player);
    }

    public boolean sendSimpleForm(
            final Player player,
            final String title,
            final String content,
            final List<String> buttons,
            final IntConsumer clickHandler
    ) {
        if (!canUseForms(player) || buttons == null || buttons.isEmpty() || clickHandler == null) {
            return false;
        }
        try {
            final Object builder = simpleFormClass.getMethod("builder").invoke(null);
            invokeStringBuilderMethod(builder, "title", title);
            invokeStringBuilderMethod(builder, "content", content == null || content.isBlank() ? " " : content);
            for (final String button : buttons) {
                invokeStringBuilderMethod(builder, "button", button == null || button.isBlank() ? " " : button);
            }
            invokeConsumerBuilderMethod(builder, response -> {
                final int clickedButtonId = clickedButtonId(response);
                if (clickedButtonId < 0 || clickedButtonId >= buttons.size()) {
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> clickHandler.accept(clickedButtonId));
            });
            final Object form = builder.getClass().getMethod("build").invoke(builder);
            sendFormMethod.invoke(floodgateApi, player.getUniqueId(), form);
            return true;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Bedrock-Formular konnte nicht gesendet werden. Es wird das Inventar-GUI genutzt.", exception);
            return false;
        }
    }

    private Method findSendFormMethod(final Class<?> apiClass) {
        for (final Method method : apiClass.getMethods()) {
            if (!method.getName().equals("sendForm") || method.getParameterCount() != 2) {
                continue;
            }
            if (method.getParameterTypes()[0].equals(UUID.class)) {
                return method;
            }
        }
        return null;
    }

    private void invokeStringBuilderMethod(final Object builder, final String name, final String value)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        for (final Method method : builder.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].equals(String.class)) {
                method.invoke(builder, value);
                return;
            }
        }
        throw new NoSuchMethodException(name + "(String)");
    }

    private void invokeConsumerBuilderMethod(final Object builder, final Consumer<Object> handler)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        for (final Method method : builder.getClass().getMethods()) {
            if (!method.getName().equals("validResultHandler") || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(Consumer.class)) {
                method.invoke(builder, handler);
                return;
            }
        }
        throw new NoSuchMethodException("validResultHandler(Consumer)");
    }

    private int clickedButtonId(final Object response) {
        if (response == null) {
            return -1;
        }
        final Integer directId = invokeInt(response, "clickedButtonId", "getClickedButtonId");
        if (directId != null) {
            return directId;
        }
        final Object button = invokeObject(response, "clickedButton", "getClickedButton");
        if (button == null) {
            return -1;
        }
        final Integer buttonId = invokeInt(button, "id", "getId", "index", "getIndex");
        return buttonId == null ? -1 : buttonId;
    }

    private Integer invokeInt(final Object target, final String... methodNames) {
        for (final String methodName : methodNames) {
            try {
                final Object result = target.getClass().getMethod(methodName).invoke(target);
                if (result instanceof Number number) {
                    return number.intValue();
                }
            } catch (final ReflectiveOperationException ignored) {
                // Try the next known Cumulus method name.
            }
        }
        return null;
    }

    private Object invokeObject(final Object target, final String... methodNames) {
        for (final String methodName : methodNames) {
            try {
                return target.getClass().getMethod(methodName).invoke(target);
            } catch (final ReflectiveOperationException ignored) {
                // Try the next known Cumulus method name.
            }
        }
        return null;
    }
}
