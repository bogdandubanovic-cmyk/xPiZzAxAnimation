package xpizzax.emoticons.client.update;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the updater inside Minecraft's first client resource-reload phase.
 * The reload future intentionally stays pending while the update is prepared,
 * so the vanilla startup/loading screen remains visible.
 */
public final class StartupUpdateReloadListener implements IdentifiableResourceReloadListener {
    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);
    private static final Identifier ID = new Identifier("xpizzaxanimation", "startup_update");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public CompletableFuture<Void> reload(
            ResourceReloader.Synchronizer synchronizer,
            ResourceManager manager,
            Profiler prepareProfiler,
            Profiler applyProfiler,
            Executor prepareExecutor,
            Executor applyExecutor
    ) {
        if (!CHECKED.compareAndSet(false, true)) {
            return synchronizer.whenPrepared(null);
        }

        XpaUpdateStatus.show("Проверка обновлений xPiZzAxAnimation...", 0.0f);

        return CompletableFuture
                .supplyAsync(GitHubUpdateChecker::checkAndPrepareUpdate, prepareExecutor)
                .thenCompose(updateReady -> synchronizer.whenPrepared(updateReady).thenCompose(ignored -> {
                    if (!Boolean.TRUE.equals(updateReady)) {
                        XpaUpdateStatus.hide();
                        return CompletableFuture.completedFuture(null);
                    }
                    return showRestartAndStopClient();
                }));
    }

    private CompletableFuture<Void> showRestartAndStopClient() {
        XpaUpdateStatus.show("Установка подготовлена. Minecraft закроется для завершения установки.", 1.0f);
        CompletableFuture<Void> stopped = new CompletableFuture<>();

        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                try {
                    // This closes the Minecraft process. The standalone Java
                    // installer waits for this PID and replaces the JAR after
                    // Windows releases the file handle.
                    client.stop();
                    stopped.complete(null);
                } catch (Throwable error) {
                    stopped.completeExceptionally(error);
                }
            });
        });

        return stopped;
    }
}
