package one.ggsky.alternativeauth.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import com.mojang.authlib.services.ProfileActionType;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.authlib.services.response.HasJoinedMinecraftServerResponse;
import com.mojang.authlib.services.response.NameAndId;
import com.mojang.authlib.services.response.ProfileAction;

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

import java.net.InetAddress;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mixin(MinecraftServicesSessionService.class)
public abstract class HasJoinedServerMixin {
    @Unique
    private static final AlternativeAuthLogger LOGGER = AlternativeAuthLoggerManager.getLogger();

    @Shadow @Final
    private MinecraftClient client;

    @Shadow
    private static Set<ProfileActionType> extractProfileActionTypes(Set<ProfileAction> response) {
        return null;
    }

    @Inject(
        at = @At("HEAD"),
        method = "hasJoinedServer",
        remap = false,
        cancellable = true
    )
    public void hasJoinedServer(String profileName, String serverId, InetAddress address, CallbackInfoReturnable<ProfileResult> cir) throws AuthenticationUnavailableException {
        AlternativeAuthConfig config = AlternativeAuthConfigManager.getConfig();

        Map<String, Object> arguments = new HashMap<>();

        arguments.put("username", profileName);
        arguments.put("serverId", serverId);

        if (address != null) {
            arguments.put("ip", address.getHostAddress());
        }

        for (AlternativeAuthProvider provider : config.getProviders()) {
            LOGGER.debug("Trying to authenticate player via " + provider.getName());
            LOGGER.debug("Using " + provider.getCheckUrl());

            URL url;

            try {
                url = AlternativeAuthUtils.appendQuery(provider.getCheckUrl(), arguments);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping provider " + provider.getName() + " with an invalid checkUrl: " + e.getMessage());
                continue;
            }

            try {
                HasJoinedMinecraftServerResponse response = client.get(url, HasJoinedMinecraftServerResponse.class);
                LOGGER.debug(provider.getName() + " session response: " + (response == null ? "null" : AlternativeAuthUtils.GSON.toJson(response)));

                if (response == null || response.id() == null) {
                    if (playerExistsOnProvider(provider, profileName)) {
                        LOGGER.warn("Player '" + profileName + "' exists on " + provider.getName() + " but failed authentication, fallback prevented");

                        cir.setReturnValue(null);
                        return;
                    }

                    continue;
                }

                PropertyMap properties = resolveProperties(provider, profileName, response);

                GameProfile profile = properties != null
                    ? new GameProfile(response.id(), profileName, properties)
                    : new GameProfile(response.id(), profileName);

                final Set<ProfileActionType> profileActions = extractProfileActionTypes(response.profileActions());

                LOGGER.debug("Authentication successful for " + profileName + " (UUID: " + response.id() + ")");
                LOGGER.info("Authenticating player via " + provider.getName());

                cir.setReturnValue(new ProfileResult(profile, profileActions));
                return;
            } catch (MinecraftClientException exception) {
                LOGGER.debug(provider.getName() + " threw during session check: " + exception.getMessage());

                if (exception.toAuthenticationException() instanceof AuthenticationUnavailableException unavailable) {
                    throw unavailable;
                }

                if (playerExistsOnProvider(provider, profileName)) {
                    LOGGER.warn("Player '" + profileName + "' exists on " + provider.getName() + " but failed authentication, fallback prevented");

                    cir.setReturnValue(null);
                    return;
                }
            }
        }

        cir.setReturnValue(null);
    }

    @Unique
    private boolean playerExistsOnProvider(AlternativeAuthProvider provider, String profileName) {
        AlternativeAuthConfig config = AlternativeAuthConfigManager.getConfig();

        if (!config.isPreventFallbackIfPlayerExists()) return false;

        String profileUrl = provider.getProfileUrl();

        if (profileUrl == null) {
            LOGGER.debug("Provider " + provider.getName() + " has no profileUrl, cannot check player existence");
            return false;
        }

        try {
            NameAndId profile = client.get(
                AlternativeAuthUtils.toUrl(profileUrl + AlternativeAuthUtils.normalizeName(profileName)),
                NameAndId.class
            );

            return profile != null;
        } catch (MinecraftClientException | IllegalArgumentException e) {
            LOGGER.debug("Could not verify player existence on " + provider.getName() + ": " + e.getMessage());
            return false;
        }
    }

    @Unique
    private PropertyMap resolveProperties(AlternativeAuthProvider provider, String profileName, HasJoinedMinecraftServerResponse response) {
        PropertyMap fallback = response.properties();

        if (fallback == null) {
            LOGGER.debug("Session response for " + profileName + " has no properties");
            return null;
        }

        String propertyUrlTemplate = provider.getPropertyUrl();

        if (propertyUrlTemplate == null) {
            LOGGER.debug("Provider " + provider.getName() + " has no propertyUrl, using session response properties");
            return fallback;
        }

        String resolvedUrl = MessageFormat.format(propertyUrlTemplate, profileName, response.id());
        LOGGER.debug(MessageFormat.format("Found {0} property URL, fetching {1}", provider.getName(), resolvedUrl));

        try {
            URL propertyUrl = AlternativeAuthUtils.toUrl(resolvedUrl);

            HasJoinedMinecraftServerResponse propertyResponse = client.get(propertyUrl, HasJoinedMinecraftServerResponse.class);

            LOGGER.debug("Property response: " + (propertyResponse == null ? "null" : AlternativeAuthUtils.GSON.toJson(propertyResponse)));

            if (propertyResponse != null && propertyResponse.properties() != null) {
                return propertyResponse.properties();
            }
        } catch (MinecraftClientException | IllegalArgumentException e) {
            LOGGER.debug("Could not fetch properties from " + provider.getName() + ", using session response properties: " + e.getMessage());
        }

        return fallback;
    }
}
