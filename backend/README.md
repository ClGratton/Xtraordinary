# Entitlement verifier

This small service verifies the one-time `xtraordinary_pro` product with the
Google Play Developer API and acknowledges a valid purchase before the Android
app grants Pro.

## Configuration

- `PLAY_PACKAGE_NAME=com.xteink.companion`
- `PLAY_PRO_PRODUCT_ID=xtraordinary_pro`
- `PORT=8080` (optional)
- Application Default Credentials for a service account that has access to the
  app in Play Console

Deploy behind HTTPS (Cloud Run is a natural fit), then build the Play flavor
with `-PentitlementBackendUrl=https://your-service.example`.

The endpoint accepts `POST /v1/google-play/verify`. It deliberately allowlists
the package and product, bounds the request body, never logs purchase tokens,
rejects pending/cancelled purchases, and acknowledges valid non-consumable
purchases on the server.
