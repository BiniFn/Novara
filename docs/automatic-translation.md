# Automatic Translation

Kototoro includes a reader-side OCR + translation workflow designed for practical Android use. This page explains what the feature does, how to set it up, and which choices matter first.

## What This Feature Does

- OCR happens inside the reading workflow
- Translation can stay local-first
- Model management is built into app settings
- Results are rendered back into the page as an overlay

## Start Here

- `Settings -> Reader -> Translation`
- `Settings -> Reader -> Translation -> Manage models`

## End-to-End Flow

1. Detect text from page images.
2. Recognize text with the selected OCR engine and models.
3. Translate locally or through a configured API.
4. Render translated text back onto the page.

## First-Time Setup

1. Open translation settings.
2. Enable translation.
3. Choose the OCR engine you want to start with.
4. Open `Manage models`.
5. Download the OCR and translation-related models you need.
6. Set source language and target language.
7. Choose local-first or API mode.

## Main User Choices

### OCR Engine

The current settings UI is centered around the most practical user-facing options:

- Fast setup
- Good starting point for lightweight use cases
- Depends on device-side ML Kit support

Hybrid OCR is also available when you want a stronger model-backed workflow and are willing to download local assets.

### Translation Mode

- `LOCAL_ONLY` keeps the workflow device-local where possible.
- `LOCAL_FIRST` prefers local processing and falls back to a configured API when needed.
- `API_ONLY` is for users who want remote translation every time.

### Languages

Set the source and target languages before tuning advanced behavior. This avoids chasing OCR or layout issues that are actually language mismatches.

## Configurable Settings

Depending on the current build and selected mode, users can configure:

- OCR engine
- Detection / recognition model selection
- Source language
- Target language
- API endpoint
- API key
- API provider preset
- API model name
- Bubble grouping behavior
- Overlay compactness
- Debug logging

## Model Management

The model management screen is used to inspect and download built-in OCR and ONNX-related models.

Current categories include:

- Recognition models
- Detection models
- Classic translation models
- General LLM models
- Bubble detector models

Downloaded status is shown per model, and missing models can be downloaded on demand from inside the app.

## Practical Recommendations

- Start simple: enable translation, choose an OCR engine, and download only the models you need.
- Use `LOCAL_FIRST` if you want local behavior without losing a remote fallback path.
- Test on a few representative pages before tuning compactness or bubble grouping.
- If you depend on offline use, complete model downloads before travel or unstable-network use.

## Common Problems

### The translation overlay does not appear

- Confirm that translation is enabled.
- Confirm source and target languages are set correctly.
- Confirm the required local models were downloaded.

### The model list is visible but downloads fail

- Check network connectivity.
- Check available storage.
- Reopen the screen and retry the download.

### API translation is not working

- Confirm endpoint, API key, and model name.
- If model discovery fails, enter the model name manually.
- Prefer `LOCAL_FIRST` while validating a new remote provider.

See also: [Troubleshooting](./troubleshooting.md)

## Advanced Notes

The project contains multiple OCR runtime paths internally, including model-backed pipelines for users who need stronger offline or hybrid workflows. The exact user-facing choices may evolve across builds, but model management remains the entry point for downloading the required local assets.

## Related Documents

- [Documentation Hub](./README.md)
- [Getting Started](./getting-started.md)
- [Reader Features](./reader-features.md)
- [OCR Architecture Review](./architecture/ocr-architecture-review.md)
- [OCR Pipeline](./architecture/ocr-pipeline-v2.md)
- [Source Integrations](./source-integrations.md)
- [FAQ](./faq.md)
- [Troubleshooting](./troubleshooting.md)
- [Development Notes](./development.md)
