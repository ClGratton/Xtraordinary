const PublisherScope = "https://www.googleapis.com/auth/androidpublisher";
const PublisherBase = "https://androidpublisher.googleapis.com/androidpublisher/v3";

export function validateVerificationRequest(body, expectedPackageName, expectedProductId) {
  if (!body || typeof body !== "object") {
    throw new RequestError(400, "A JSON request body is required.");
  }
  const { packageName, productId, purchaseToken } = body;
  if (packageName !== expectedPackageName || productId !== expectedProductId) {
    throw new RequestError(403, "The requested product is not allowed.");
  }
  if (typeof purchaseToken !== "string" || purchaseToken.length < 8 || purchaseToken.length > 4096) {
    throw new RequestError(400, "The purchase token is invalid.");
  }
  return { packageName, productId, purchaseToken };
}

export async function verifyAndAcknowledgePurchase({
  auth,
  fetchImpl = fetch,
  packageName,
  productId,
  purchaseToken,
}) {
  const client = await auth.getClient();
  const headers = await client.getRequestHeaders();
  const encodedPackage = encodeURIComponent(packageName);
  const encodedToken = encodeURIComponent(purchaseToken);
  const lookupUrl =
    `${PublisherBase}/applications/${encodedPackage}/purchases/productsv2/tokens/${encodedToken}`;
  const lookup = await fetchImpl(lookupUrl, { headers });
  if (!lookup.ok) {
    if (lookup.status === 404) return { entitled: false, reason: "not_found" };
    throw new UpstreamError(`Google Play verification failed with HTTP ${lookup.status}.`);
  }

  const purchase = await lookup.json();
  const purchased = purchase.purchaseStateContext?.purchaseState === "PURCHASED";
  const expectedLineItem = purchase.productLineItem?.some((item) => item.productId === productId);
  if (!purchased || !expectedLineItem) {
    return { entitled: false, reason: purchased ? "wrong_product" : "not_purchased" };
  }

  if (purchase.acknowledgementState !== "ACKNOWLEDGED") {
    const encodedProduct = encodeURIComponent(productId);
    const acknowledgeUrl =
      `${PublisherBase}/applications/${encodedPackage}/purchases/products/` +
      `${encodedProduct}/tokens/${encodedToken}:acknowledge`;
    const acknowledgement = await fetchImpl(acknowledgeUrl, {
      method: "POST",
      headers: { ...headers, "content-type": "application/json" },
      body: "{}",
    });
    if (!acknowledgement.ok) {
      throw new UpstreamError(`Google Play acknowledgement failed with HTTP ${acknowledgement.status}.`);
    }
  }

  return { entitled: true };
}

export async function createGoogleAuth() {
  const { GoogleAuth } = await import("google-auth-library");
  return new GoogleAuth({ scopes: [PublisherScope] });
}

export class RequestError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export class UpstreamError extends Error {}
