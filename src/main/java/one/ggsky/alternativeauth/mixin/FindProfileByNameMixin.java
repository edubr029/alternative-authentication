package one.ggsky.alternativeauth.mixin;

import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.services.MinecraftServicesProfileRepository;
import com.mojang.authlib.services.response.NameAndId;
import one.ggsky.alternativeauth.config.AlternativeAuthConfig;
import one.ggsky.alternativeauth.config.AlternativeAuthConfigManager;
import one.ggsky.alternativeauth.config.AlternativeAuthProvider;
import one.ggsky.alternativeauth.logger.AlternativeAuthLogger;
import one.ggsky.alternativeauth.logger.AlternativeAuthLoggerManager;
import one.ggsky.alternativeauth.util.AlternativeAuthUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(MinecraftServicesProfileRepository.class)
public class FindProfileByNameMixin {
    @Unique
    private static final AlternativeAuthLogger LOGGER = AlternativeAuthLoggerManager.getLogger();

    @Shadow @Final
    private MinecraftClient client;

    @Inject(
        at = @At("HEAD"),
        method = "findProfileByName",
        remap = false,
        cancellable = true
    )
    private void findProfileByName(String name, CallbackInfoReturnable<Optional<NameAndId>> cir) {
        AlternativeAuthConfig config = AlternativeAuthConfigManager.getConfig();

        for (AlternativeAuthProvider provider : config.getProviders()) {
            LOGGER.debug("Trying provider " + provider.getName());

            String profileUrl = provider.getProfileUrl();

            if (profileUrl == null) {
                LOGGER.debug("Provider " + provider.getName() + " has no profileUrl, cannot look up profile");
                continue;
            }

            try {
                NameAndId profile = client.get(AlternativeAuthUtils.toUrl(profileUrl + AlternativeAuthUtils.normalizeName(name)), NameAndId.class);

                if (profile != null) {
                    LOGGER.debug("Resolved '" + name + "' to UUID " + profile.id());

                    cir.setReturnValue(Optional.of(profile));
                    return;
                }

                LOGGER.debug("Provider " + provider.getName() + " returned no result");
            } catch (MinecraftClientException | IllegalArgumentException e) {
                LOGGER.debug("Provider " + provider.getName() + " failed: " + e.getMessage());
            }
        }

        LOGGER.warn("Couldn't find profile with name: " + name);
        cir.setReturnValue(Optional.empty());
    }
}
