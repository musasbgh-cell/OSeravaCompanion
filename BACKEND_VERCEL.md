# Serava Vercel Backend

This repository contains a Vercel Serverless Function at `api/serava.js`.

## Deploy

Import this GitHub repository into a personal Vercel project. No paid Team is required.

## Required environment variable

Set this in Vercel Project Settings -> Environment Variables:

- `OPENAI_API_KEY` = your OpenAI API key

Optional:

- `OPENAI_MODEL` = `gpt-6-luna`
- `SERAVA_SAFETY_SALT` = a long random secret

Never put an OpenAI API key in the Android source or APK.

## Endpoints

- `GET /health`
- `POST /v1/serava/respond`

The Android app already expects `/v1/serava/respond`.
