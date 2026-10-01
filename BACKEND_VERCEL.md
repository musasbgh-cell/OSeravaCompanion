# Serava Vercel Backend — Free Gemini Tier

The Serava backend runs as a Vercel Serverless Function at `api/serava.js`.

## Deploy

Import this GitHub repository into a personal Vercel Hobby project. A paid Vercel Team is not required.

## Required environment variable

In Vercel Project Settings -> Environment Variables add:

- `GEMINI_API_KEY` = your Google Gemini Developer API key

Optional:

- `GEMINI_MODEL` = `gemini-3.7-flash`

Do not place the Gemini key inside the Android app or GitHub source.

## Endpoints

- `GET /health`
- `POST /v1/serava/respond`

The Android app already uses `/v1/serava/respond`.

## Notes

The Gemini Developer API has a free tier with usage limits. The backend automatically keeps the provider key on Vercel, not inside the APK.
