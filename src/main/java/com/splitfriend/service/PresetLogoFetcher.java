package com.splitfriend.service;

import java.util.Optional;

/** Downloads the raw icon for a brand's domain. Implementations must not throw. */
@FunctionalInterface
public interface PresetLogoFetcher {

    Optional<byte[]> fetch(String domain);
}
