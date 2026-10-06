package one.ggsky.alternativeauth.mixin;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.services.MinecraftServicesProfileRepository;
import com.mojang.authlib.services.ProfileNotFoundException;
import com.mojang.authlib.services.response.NameAndId;
import com.mojang.authlib.services.response.ProfileSearchResultsResponse;

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

@Mixin(MinecraftServicesProfileRepository.class)
public class FindProfilesByNamesMixin {
    @Unique
    private static final AlternativeAuthLogger LOGGER = AlternativeAuthLoggerManager.getLogger();

    @Shadow @Final
    private MinecraftClient client;

    @Unique
    private static final int ENTRIES_PER_PAGE = 10;
    @Unique
    private static final int MAX_FAIL_COUNT = 3;
    @Unique
    private static final int DELAY_BETWEEN_PAGES = 100;
    @Unique
    private static final int DELAY_BETWEEN_FAILURES = 750;

    @Inject(
        at = @At("HEAD"),
        method = "findProfilesByNames",
        remap = false,
        cancellable = true
    )
    public void findProfilesByNames(String[] names, ProfileLookupCallback callback, CallbackInfo ci) {
        final Set<String> criteria = Arrays.stream(names)
            .filter(name -> !Strings.isNullOrEmpty(name))
            .collect(Collectors.toSet());

        final AlternativeAuthConfig config = AlternativeAuthConfigManager.getConfig();
        int page = 0;

        for (final List<String> request : Iterables.partition(criteria, ENTRIES_PER_PAGE)) {
            final List<String> normalizedRequest = request.stream().map(AlternativeAuthUtils::normalizeName).toList();

            int failCount = 0;
            boolean failed;

            do {
                failed = false;

                try {
                    final Map<String, NameAndId> profiles = new HashMap<>();

                    for (AlternativeAuthProvider provider : config.getProviders()) {
                        URL url;

                        try {
                            url = AlternativeAuthUtils.toUrl(provider.getProfilesUrl());
                        } catch (IllegalArgumentException e) {
                            LOGGER.error("Skipping provider " + provider.getName() + " with an invalid profilesUrl: " + e.getMessage());
                            continue;
                        }

                        ProfileSearchResultsResponse response = client.post(url, normalizedRequest, ProfileSearchResultsResponse.class);

                        if (response == null || response.profiles().isEmpty()) {
                            LOGGER.debug(MessageFormat.format("Response from {0} provider is either null or contains no elements", provider.getName()));
                            continue;
                        }

                        LOGGER.debug(MessageFormat.format("Response from {0} provider is not null and contains at least 1 element", provider.getName()));

                        for (final NameAndId profile : response.profiles()) {
                            profiles.putIfAbsent(AlternativeAuthUtils.normalizeName(profile.name()), profile);
                        }
                    }

                    failCount = 0;

                    LOGGER.debug(MessageFormat.format("Page {0} returned {1} results, parsing", page, profiles.size()));

                    for (final NameAndId profile : profiles.values()) {
                        LOGGER.debug(MessageFormat.format("Successfully looked up profile {0}", profile));
                        callback.onProfileLookupSucceeded(profile.name(), profile.id());
                    }

                    for (final String name : request) {
                        if (profiles.containsKey(AlternativeAuthUtils.normalizeName(name))) {
                            continue;
                        }

                        LOGGER.debug(MessageFormat.format("Could not find profile {0}", name));
                        callback.onProfileLookupFailed(name, new ProfileNotFoundException("Server did not find the requested profile"));
                    }

                    page++;

                    try {
                        Thread.sleep(DELAY_BETWEEN_PAGES);
                    } catch (final InterruptedException ignored) {
                    }
                } catch (final MinecraftClientException exception) {
                    failCount++;

                    if (failCount == MAX_FAIL_COUNT) {
                        for (final String name : request) {
                            LOGGER.debug(MessageFormat.format("Could not find profile {0} because of a server error", name));
                            callback.onProfileLookupFailed(name, exception.toAuthenticationException());
                        }
                    } else {
                        try {
                            Thread.sleep(DELAY_BETWEEN_FAILURES);
                        } catch (final InterruptedException ignored) {
                        }

                        failed = true;
                    }
                }
            } while (failed);
        }

        ci.cancel();
    }
}
