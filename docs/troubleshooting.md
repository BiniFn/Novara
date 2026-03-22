# Troubleshooting

This page collects the most common setup and integration problems.

## Sources Do Not Appear

### Mihon or Aniyomi sources are missing

1. Confirm the extension APK is actually installed on the device.
2. If you use in-app repositories, confirm the repository synced and the extension finished installing.
3. Reopen Kototoro and refresh the `Settings -> Content Sources -> Extensions` screen.
4. Check whether the source appears in `Browse -> Content Sources`.
5. If the source is still missing, check compatibility notes for that ecosystem.

### Legado or TVBox sources are missing

1. Confirm the JSON file or JSON URL is valid and reachable.
2. Re-import it from `Settings -> Content Sources -> Import JSON Sources`.
3. Open `Settings -> Content Sources -> JSON Sources Directory` and confirm the imported source is listed and enabled.
4. Check whether it appears in `Browse -> Content Sources`.
5. Test with one source first before importing many.

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
