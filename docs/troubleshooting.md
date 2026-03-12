# Troubleshooting

This page collects the most common setup and integration problems.

## Sources Do Not Appear

### Mihon or Aniyomi sources are missing

1. Confirm the companion app is installed.
2. Confirm the extensions were installed there successfully.
3. Reopen Kototoro and refresh source detection.
4. If the source is still missing, check compatibility notes for that ecosystem.

### Legado sources are missing

1. Confirm your Legado source definitions are available on the device.
2. Reopen the source management area in Kototoro.
3. Test with one source first before importing many.

## Automatic Translation Is Not Ready

### The translation feature is enabled but nothing happens

1. Recheck source and target languages.
2. Open `Manage models` and confirm the required models were downloaded.
3. Test on a page with clearly visible text.

### Model downloads fail

1. Check network connectivity.
2. Check device storage.
3. Reopen the model management screen and retry.

### Remote API translation fails

1. Recheck endpoint, API key, and model name.
2. Try manual model entry if model discovery fails.
3. Use `LOCAL_FIRST` while validating a new provider.

## WebDAV Sync Problems

### Devices do not show the same data

1. Pick the device with the newest state.
2. Create a manual backup there.
3. Restore that backup on the other device.
4. Resume normal sync only after both devices match.

### Backup or restore fails

1. Recheck the full WebDAV endpoint path.
2. Verify credentials.
3. Test with a dedicated backup directory.

## External Integrations Are Unstable

- Upstream websites change frequently.
- Extension compatibility can differ by version.
- A source working in a companion app does not always guarantee identical behavior in Kototoro.

## More References

- [Documentation Hub](./README.md)
- [Getting Started](./getting-started.md)
- [Automatic Translation](./automatic-translation.md)
- [Source Integrations](./source-integrations.md)
- [WebDAV Sync](./webdav-sync.md)
- [FAQ](./faq.md)
- [Mihon Integration Reference](./reference/mihon-integration.md)
