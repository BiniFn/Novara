# WebDAV Sync

Kototoro uses WebDAV for multi-device backup and synchronization.

## Why WebDAV

WebDAV is attractive for this project because it is:

- Open and widely supported
- Usable with self-hosted services
- Usable with many free or existing storage setups
- Independent from a vendor-locked cloud account

## What Gets Synced

Kototoro can sync user data such as:

- Favorites
- Reading history
- Reading progress
- Groups
- Login credentials and related source state where applicable

## Why It Is Reliable

Kototoro's sync flow is designed around backup / restore and timestamp-based merging, which makes it practical for switching between devices without manually exporting data every time.

## Recommended Setup

1. Open `Settings`.
2. Go to `Backup & Restore`.
3. Configure your WebDAV endpoint, username, and password.
4. Test backup / restore on one device first.
5. Reuse the same WebDAV target on your other devices.

## First Sync Checklist

1. Pick one device as the source of truth.
2. Create a fresh backup there.
3. Restore that backup on the second device.
4. Confirm favorites, history, and progress look correct.
5. Start normal sync only after the initial state is consistent.

## Practical Recommendations

- Use a dedicated directory for Kototoro backups.
- Verify restore behavior before depending on the setup.
- If multiple devices are used heavily, sync regularly instead of waiting for long gaps.

## Common Problems

### Data looks different across devices

- Run a manual backup on the device with the newest state.
- Restore from that same backup on the lagging device.
- Avoid letting long-unsynced devices drift for too long.

### WebDAV credentials work in one app but not in Kototoro

- Recheck the exact endpoint path.
- Confirm username and password separately.
- Test with a dedicated directory instead of the server root.

## Related Documents

- [Documentation Hub](./README.md)
- [Getting Started](./getting-started.md)
- [Reader Features](./reader-features.md)
- [FAQ](./faq.md)
- [Troubleshooting](./troubleshooting.md)
