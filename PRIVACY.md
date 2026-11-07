# Mukhar privacy policy

_Last updated: 8 November 2026 · applies to Mukhar 1.0 (Android)_

Mukhar is a free, open-source app (GPL-3.0) made by one independent developer. It has **no server, no account, no ads and no trackers**.

## What stays on your phone
Everything you create in Mukhar stays on your phone:
- talk recordings and transcripts
- feedback
- words and revision history
- streaks and settings

A talk's recording is deleted once its report is written, unless you choose to keep it. If the transcript couldn't be written (no speech-to-text key, offline, or the service failed), the recording is kept on the phone so the transcript can be written later. **Settings → Recordings** lists every recording, lets you play or delete them, and limits the space they take (500 MB by default; the oldest are deleted first).

Your phone's own Android backup may include this data, unless you turn backup off. Your API keys are never included in backups.

## When data leaves your phone
Only if **you** add your own API keys in Settings → Analysis → Your keys:

| Service (you choose) | What your phone sends | Why |
|---|---|---|
| Deepgram, or Sarvam AI | The talk's audio | To turn speech into text |
| Anthropic (Claude) | The transcript and your talk's metrics (no audio) | To write coaching feedback |

- These requests go **directly from your phone** to the service, under **your own account**. That service's privacy policy and data settings apply.
- Mukhar's developer never receives your keys, audio, transcripts or feedback.
- Keys are stored encrypted on the device (Android Keystore), are excluded from backups and device transfer, and are deleted by "Delete my data".

Without keys, nothing is sent anywhere.

## Usage and crash data
Mukhar 1.0 collects **no** usage, analytics or crash data.

## Permissions
- **Microphone:** to record your talks and sentences.
- **Notifications:** for the daily reminder you set.
- **Internet:** used only for the services you add keys for.

## Children
Mukhar is meant for people aged 18 and over.

## Deleting your data
Settings → Delete my data removes everything Mukhar stored on the phone, including your keys. Uninstalling the app does the same.

## Contact
Open an issue at https://github.com/ukpratik/mukhar/issues.
