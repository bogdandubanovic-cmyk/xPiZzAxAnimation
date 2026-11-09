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
 * Runs the updater as part of Minecraft's real client resource-reload pipeline.
 * The reload future keeps the vanilla loading overlay visible while the update
 * is checked, downloaded and verified.
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

        return CompletableFuture
                .supplyAsync(GitHubUpdateChecker::checkAndPrepareUpdate, prepareExecutor)
                .thenCompose(synchronizer::whenPrepared)
                .thenCompose(updateReady -> {
                    if (!Boolean.TRUE.equals(updateReady)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return waitThenStopMinecraft();
                });
    }

    private CompletableFuture<Void> waitThenStopMinecraft() {
        CompletableFuture<Void> stopSignal = new CompletableFuture<>();

        // Keep the resource-loading screen visible long enough for the user to
        // read the message. The standalone installer is already waiting for
        // this Minecraft process to exit.
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                XpaUpdateStatus.show("Обновление подготовлено. Закрываем Minecraft для установки...");
                try {
                    client.stop();
                } finally {
                    stopSignal.complete(null);
                }
            });
        });

        return stopSignal;
    }
}
