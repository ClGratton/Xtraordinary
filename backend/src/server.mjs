import http from "node:http";
import {
  RequestError,
  UpstreamError,
  createGoogleAuth,
  validateVerificationRequest,
  verifyAndAcknowledgePurchase,
} from "./play-verification.mjs";

const Port = Number.parseInt(process.env.PORT ?? "8080", 10);
const ExpectedPackageName = requireEnvironment("PLAY_PACKAGE_NAME");
const ExpectedProductId = requireEnvironment("PLAY_PRO_PRODUCT_ID");
const auth = await createGoogleAuth();

const server = http.createServer(async (request, response) => {
  response.setHeader("content-type", "application/json; charset=utf-8");
  if (request.method === "GET" && request.url === "/healthz") {
    return sendJson(response, 200, { ok: true });
  }
  if (request.method !== "POST" || request.url !== "/v1/google-play/verify") {
    return sendJson(response, 404, { error: "not_found" });
  }

  try {
    const body = await readJsonBody(request);
    const verifiedRequest = validateVerificationRequest(
      body,
      ExpectedPackageName,
      ExpectedProductId,
    );
    const result = await verifyAndAcknowledgePurchase({ auth, ...verifiedRequest });
    return sendJson(response, 200, result);
  } catch (error) {
    if (error instanceof RequestError) {
      return sendJson(response, error.status, { error: error.message });
    }
    if (error instanceof UpstreamError) {
      console.error(error.message);
      return sendJson(response, 503, { error: "play_verification_unavailable" });
    }
    console.error("Unexpected entitlement verification failure", error);
    return sendJson(response, 500, { error: "internal_error" });
  }
});

server.listen(Port, "0.0.0.0", () => {
  console.log(`Xtraordinary entitlement verifier listening on ${Port}`);
});

function requireEnvironment(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

function readJsonBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > 16 * 1024) {
        reject(new RequestError(413, "The request body is too large."));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(new RequestError(400, "The request body is not valid JSON."));
      }
    });
    request.on("error", reject);
  });
}

function sendJson(response, status, body) {
  response.statusCode = status;
  response.end(JSON.stringify(body));
}
