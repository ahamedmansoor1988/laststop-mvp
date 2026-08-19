const INDIA = "in";

export default async function handler(request, response) {
  setCors(response);

  if (request.method === "OPTIONS") {
    response.status(204).end();
    return;
  }

  const query = String(request.query?.q || "").replace(/\s+/g, " ").trim();

  if (query.length < 3) {
    response.status(400).json({ error: "Query must be at least 3 characters" });
    return;
  }

  try {
    const results = await searchPlaces(query);
    response.status(200).json({ results });
  } catch (error) {
    response.status(500).json({ error: error.message || "Search failed" });
  }
}

function setCors(response) {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  response.setHeader("Access-Control-Allow-Headers", "Content-Type");
}

async function searchPlaces(query) {
  const seen = new Set();
  const configuredProviders = [];

  if (process.env.GOOGLE_MAPS_API_KEY) {
    configuredProviders.push(() => searchGooglePlaces(query));
  }

  if (process.env.GEOAPIFY_API_KEY) {
    configuredProviders.push(() => searchGeoapify(query));
  }

  if (process.env.LOCATIONIQ_API_KEY) {
    configuredProviders.push(() => searchLocationIq(query));
  }

  const providers = configuredProviders.length ? configuredProviders : [() => searchOpenStreetMap(query)];

  for (const provider of providers) {
    const results = await provider();
    const merged = [];
    results.forEach((place) => {
      const key = `${Number(place.lat).toFixed(6)}:${Number(place.lon).toFixed(6)}:${place.name}`;
      if (seen.has(key)) return;
      seen.add(key);
      merged.push(place);
    });
    if (merged.length) return merged.slice(0, 8);
  }

  return [];
}

async function searchGooglePlaces(query) {
  const url = new URL("https://places.googleapis.com/v1/places:searchText");
  const response = await fetch(url.toString(), {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-goog-api-key": process.env.GOOGLE_MAPS_API_KEY,
      "x-goog-fieldmask": "places.displayName,places.formattedAddress,places.location"
    },
    body: JSON.stringify({
      textQuery: addIndiaBias(query),
      pageSize: 8,
      regionCode: "IN",
      locationBias: {
        circle: {
          center: {
            latitude: 12.9716,
            longitude: 77.5946
          },
          radius: 50000
        }
      }
    })
  });

  if (!response.ok) {
    throw new Error(`Google Places returned ${response.status}`);
  }

  const data = await response.json();
  return (data.places || [])
    .filter((place) => place.location)
    .map((place) => ({
      name: place.displayName?.text || firstPart(place.formattedAddress),
      display_name: place.formattedAddress || place.displayName?.text || "India",
      lat: place.location.latitude,
      lon: place.location.longitude,
      provider: "Google"
    }));
}

async function searchGeoapify(query) {
  const url = new URL("https://api.geoapify.com/v1/geocode/search");
  url.searchParams.set("text", addIndiaBias(query));
  url.searchParams.set("filter", `countrycode:${INDIA}`);
  url.searchParams.set("bias", "proximity:77.5946,12.9716");
  url.searchParams.set("format", "json");
  url.searchParams.set("limit", "8");
  url.searchParams.set("apiKey", process.env.GEOAPIFY_API_KEY);

  const data = await fetchJson(url);
  return (data.results || []).map((place) => ({
    name: place.name || place.address_line1 || "Destination",
    display_name: place.formatted || [place.address_line1, place.address_line2].filter(Boolean).join(", "),
    lat: place.lat,
    lon: place.lon,
    provider: "Geoapify"
  }));
}

async function searchLocationIq(query) {
  const url = new URL("https://api.locationiq.com/v1/autocomplete");
  url.searchParams.set("q", addIndiaBias(query));
  url.searchParams.set("countrycodes", INDIA);
  url.searchParams.set("limit", "8");
  url.searchParams.set("normalizecity", "1");
  url.searchParams.set("dedupe", "1");
  url.searchParams.set("key", process.env.LOCATIONIQ_API_KEY);

  const data = await fetchJson(url);
  return (Array.isArray(data) ? data : []).map((place) => ({
    name: place.display_place || place.namedetails?.name || firstPart(place.display_name),
    display_name: place.display_name || place.display_address || "India",
    lat: Number(place.lat),
    lon: Number(place.lon),
    provider: "LocationIQ"
  }));
}

async function searchOpenStreetMap(query) {
  const [nominatim, photon] = await Promise.allSettled([fetchNominatim(query), fetchPhoton(query)]);
  return [
    ...(nominatim.status === "fulfilled" ? nominatim.value : []),
    ...(photon.status === "fulfilled" ? photon.value : [])
  ];
}

async function fetchNominatim(query) {
  const url = new URL("https://nominatim.openstreetmap.org/search");
  url.searchParams.set("q", addIndiaBias(query));
  url.searchParams.set("format", "jsonv2");
  url.searchParams.set("limit", "6");
  url.searchParams.set("addressdetails", "1");
  url.searchParams.set("countrycodes", INDIA);

  const data = await fetchJson(url);
  return (Array.isArray(data) ? data : []).map((place) => ({
    name: place.name || firstPart(place.display_name),
    display_name: place.display_name,
    lat: Number(place.lat),
    lon: Number(place.lon),
    provider: "OpenStreetMap"
  }));
}

async function fetchPhoton(query) {
  const url = new URL("https://photon.komoot.io/api/");
  url.searchParams.set("q", addIndiaBias(query));
  url.searchParams.set("limit", "6");
  url.searchParams.set("lang", "en");

  const data = await fetchJson(url);
  return (data.features || [])
    .filter((feature) => feature?.properties?.countrycode === "IN" && Array.isArray(feature.geometry?.coordinates))
    .map((feature) => {
      const [lon, lat] = feature.geometry.coordinates;
      const props = feature.properties || {};
      const name = props.name || props.street || props.city || "Destination";
      const displayName = [props.name, props.street, props.locality, props.city, props.state, props.country]
        .filter(Boolean)
        .join(", ");
      return {
        name,
        display_name: displayName || name,
        lat,
        lon,
        provider: "OpenStreetMap"
      };
    });
}

async function fetchJson(url) {
  const response = await fetch(url.toString(), {
    headers: {
      accept: "application/json",
      "user-agent": "LastStopMVP/0.1"
    }
  });

  if (!response.ok) {
    throw new Error(`Provider returned ${response.status}`);
  }

  return response.json();
}

function addIndiaBias(query) {
  return /india|bharat/i.test(query) ? query : `${query}, India`;
}

function firstPart(value) {
  return String(value || "Destination").split(",")[0].trim() || "Destination";
}
