import assert from "node:assert/strict";
import test from "node:test";
import {
  RequestError,
  validateVerificationRequest,
  verifyAndAcknowledgePurchase,
} from "../src/play-verification.mjs";

const packageName = "com.xteink.companion";
const productId = "xtraordinary_pro";
const purchaseToken = "valid-purchase-token";
const auth = {
  async getClient() {
    return { async getRequestHeaders() { return { authorization: "Bearer test" }; } };
  },
};

test("request validation allows only the configured app and product", () => {
  assert.deepEqual(
    validateVerificationRequest({ packageName, productId, purchaseToken }, packageName, productId),
    { packageName, productId, purchaseToken },
  );
  assert.throws(
    () => validateVerificationRequest(
      { packageName: "another.app", productId, purchaseToken },
      packageName,
      productId,
    ),
    (error) => error instanceof RequestError && error.status === 403,
  );
});

test("a purchased matching product is acknowledged and entitled", async () => {
  const requests = [];
  const result = await verifyAndAcknowledgePurchase({
    auth,
    packageName,
    productId,
    purchaseToken,
    fetchImpl: async (url, options = {}) => {
      requests.push({ url, options });
      if (requests.length === 1) {
        return response(200, {
          purchaseStateContext: { purchaseState: "PURCHASED" },
          productLineItem: [{ productId }],
          acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
        });
      }
      return response(200, {});
    },
  });

  assert.deepEqual(result, { entitled: true });
  assert.equal(requests.length, 2);
  assert.match(requests[1].url, /:acknowledge$/);
  assert.equal(requests[1].options.method, "POST");
});

test("pending and mismatched purchases never grant entitlement", async () => {
  const pending = await verifyAndAcknowledgePurchase({
    auth,
    packageName,
    productId,
    purchaseToken,
    fetchImpl: async () => response(200, {
      purchaseStateContext: { purchaseState: "PENDING" },
      productLineItem: [{ productId }],
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    }),
  });
  assert.deepEqual(pending, { entitled: false, reason: "not_purchased" });

  const wrongProduct = await verifyAndAcknowledgePurchase({
    auth,
    packageName,
    productId,
    purchaseToken,
    fetchImpl: async () => response(200, {
      purchaseStateContext: { purchaseState: "PURCHASED" },
      productLineItem: [{ productId: "another_product" }],
      acknowledgementState: "ACKNOWLEDGED",
    }),
  });
  assert.deepEqual(wrongProduct, { entitled: false, reason: "wrong_product" });
});

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() { return body; },
  };
}
