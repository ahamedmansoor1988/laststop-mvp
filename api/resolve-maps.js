export default async function handler(request, response) {
  setCors(response);

  if (request.method === "OPTIONS") {
    response.status(204).end();
    return;
  }

  const rawUrl = request.query?.url;

  if (!rawUrl || typeof rawUrl !== "string") {
    response.status(400).json({ error: "Missing url" });
    return;
  }

  let target;
  try {
    target = new URL(rawUrl);
  } catch {
    response.status(400).json({ error: "Invalid url" });
    return;
  }

  const allowedHosts = ["maps.app.goo.gl", "goo.gl", "google.com", "www.google.com", "maps.google.com"];
  const allowed = allowedHosts.some((host) => target.hostname === host || target.hostname.endsWith(`.${host}`));

  if (!allowed) {
    response.status(400).json({ error: "Only Google Maps links are supported" });
    return;
  }

  try {
    const resolved = await fetch(target.toString(), {
      redirect: "follow",
      headers: {
        "user-agent": "LastStopMVP/0.1"
      }
    });

    response.status(200).json({ url: resolved.url || target.toString() });
  } catch {
    response.status(502).json({ error: "Could not resolve link" });
  }
}

function setCors(response) {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  response.setHeader("Access-Control-Allow-Headers", "Content-Type");
}
