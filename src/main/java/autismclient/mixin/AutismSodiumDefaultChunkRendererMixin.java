package autismclient.mixin;

import autismclient.AutismClientAddon;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class AutismSodiumDefaultChunkRendererMixin {

    @Unique private static volatile boolean autism$loggedResourcelessSkip;

    @WrapMethod(method = "render")
    private void autism$guardRender(@Coerce Object matrices, @Coerce Object lists, @Coerce Object pass,
                                    @Coerce Object camera, @Coerce Object fog, boolean useBlockFaceCulling,
                                    @Coerce Object sampler, @Coerce Object globalsBuffer, @Coerce Object sectionBuffer,
                                    Operation<Void> original) {
        try {
            original.call(matrices, lists, pass, camera, fog, useBlockFaceCulling, sampler, globalsBuffer, sectionBuffer);
        } catch (NullPointerException error) {
            String message = error.getMessage();

            if (message == null || !message.contains("getResources")) {
                throw error;
            }
            if (!autism$loggedResourcelessSkip) {
                autism$loggedResourcelessSkip = true;
                AutismClientAddon.LOG.warn("[Autism] Skipped a Sodium terrain pass with a resource-less render region to avoid a render-thread crash (will not log again).");
            }
        }
    }
}
