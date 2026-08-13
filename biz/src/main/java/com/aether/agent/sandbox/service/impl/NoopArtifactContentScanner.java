package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.service.ArtifactContentScanner;

/** Explicit opt-out adapter for tests or an organization-provided external scanner. */
public class NoopArtifactContentScanner implements ArtifactContentScanner {
    @Override public ScanResult scan(String fileName, String contentType, byte[] content) { return ScanResult.allowed(); }
}
