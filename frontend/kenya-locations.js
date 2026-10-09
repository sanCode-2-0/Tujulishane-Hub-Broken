// County and Sub-County data for Kenya
const kenyaLocations = {
  Mombasa: ["Changamwe", "Jomvu", "Kisauni", "Likoni", "Mvita", "Nyali"],
  Kwale: ["Kinango", "Lunga Lunga", "Msambweni", "Matuga"],
  Kilifi: [
    "Ganze",
    "Kaloleni",
    "Kilifi North",
    "Kilifi South",
    "Magarini",
    "Malindi",
    "Rabai",
  ],
  "Tana River": ["Bura", "Galole", "Garsen"],
  Lamu: ["Lamu East", "Lamu West"],
  "Taita-Taveta": ["Mwatate", "Taveta", "Voi", "Wundanyi"],
  Garissa: [
    "Daadab",
    "Fafi",
    "Garissa Township",
    "Hulugho",
    "Ijara",
    "Lagdera",
    "Balambala",
  ],
  Wajir: [
    "Eldas",
    "Tarbaj",
    "Wajir East",
    "Wajir North",
    "Wajir South",
    "Wajir West",
  ],
  Mandera: [
    "Banissa",
    "Lafey",
    "Mandera East",
    "Mandera North",
    "Mandera South",
    "Mandera West",
  ],
  Marsabit: ["Laisamis", "Moyale", "North Horr", "Saku"],
  Isiolo: ["Isiolo", "Merti", "Garbatulla"],
  Meru: [
    "Buuri",
    "Igembe Central",
    "Igembe North",
    "Igembe South",
    "Imenti Central",
    "Imenti North",
    "Imenti South",
    "Tigania East",
    "Tigania West",
  ],
  "Tharaka-Nithi": [
    "Tharaka North",
    "Tharaka South",
    "Chuka",
    "Igambang'ombe",
    "Maara",
    "Chiakariga",
    "Muthambi",
  ],
  Embu: ["Manyatta", "Mbeere North", "Mbeere South", "Runyenjes"],
  Kitui: [
    "Kitui West",
    "Kitui Central",
    "Kitui Rural",
    "Kitui South",
    "Kitui East",
    "Mwingi North",
    "Mwingi West",
    "Mwingi Central",
  ],
  Machakos: [
    "Kathiani",
    "Machakos Town",
    "Masinga",
    "Matungulu",
    "Mavoko",
    "Mwala",
    "Yatta",
  ],
  Makueni: [
    "Kaiti",
    "Kibwezi West",
    "Kibwezi East",
    "Kilome",
    "Makueni",
    "Mbooni",
  ],
  Nyandarua: ["Kinangop", "Kipipiri", "Ndaragwa", "Ol-Kalou", "Ol Joro Orok"],
  Nyeri: [
    "Kieni East",
    "Kieni West",
    "Mathira East",
    "Mathira West",
    "Mukurweini",
    "Nyeri Town",
    "Othaya",
    "Tetu",
  ],
  Kirinyaga: [
    "Kirinyaga Central",
    "Kirinyaga East",
    "Kirinyaga West",
    "Mwea East",
    "Mwea West",
  ],
  "Murang'a": [
    "Gatanga",
    "Kahuro",
    "Kandara",
    "Kangema",
    "Kigumo",
    "Kiharu",
    "Mathioya",
    "Murang'a South",
  ],
  Kiambu: [
    "Gatundu North",
    "Gatundu South",
    "Githunguri",
    "Juja",
    "Kabet",
    "Kiambaa",
    "Kiambu",
    "Kikuyu",
    "Limuru",
    "Ruiru",
    "Thika Town",
    "Lari",
  ],
  Turkana: [
    "Loima",
    "Turkana Central",
    "Turkana East",
    "Turkana North",
    "Turkana South",
  ],
  "West Pokot": ["Central Pokot", "North Pokot", "Pokot South", "West Pokot"],
  Samburu: ["Samburu East", "Samburu North", "Samburu West"],
  "Trans-Nzoia": ["Cherangany", "Endebess", "Kiminini", "Kwanza", "Saboti"],
  "Uasin Gishu": ["Ainabkoi", "Kapseret", "Kesses", "Moiben", "Soy", "Turbo"],
  "Elgeyo-Marakwet": [
    "Keiyo North",
    "Keiyo South",
    "Marakwet East",
    "Marakwet West",
  ],
  Nandi: ["Aldai", "Chesumei", "Emgwen", "Mosop", "Nandi Hills", "Tindiret"],
  Baringo: [
    "Baringo Central",
    "Baringo North",
    "Baringo South",
    "Eldama Ravine",
    "Mogotio",
    "Tiaty",
  ],
  Laikipia: [
    "Laikipia Central",
    "Laikipia East",
    "Laikipia North",
    "Laikipia West",
    "Nyahururu",
  ],
  Nakuru: [
    "Bahati",
    "Gilgil",
    "Kuresoi North",
    "Kuresoi South",
    "Molo",
    "Naivasha",
    "Nakuru Town East",
    "Nakuru Town West",
    "Njoro",
    "Rongai",
    "Subukia",
  ],
  Narok: [
    "Narok East",
    "Narok North",
    "Narok South",
    "Narok West",
    "Transmara East",
    "Transmara West",
  ],
  Kajiado: [
    "Isinya",
    "Kajiado Central",
    "Kajiado North",
    "Loitokitok",
    "Mashuuru",
  ],
  Kericho: [
    "Ainamoi",
    "Belgut",
    "Bureti",
    "Kipkelion East",
    "Kipkelion West",
    "Soin/Sigowet",
  ],
  Bomet: ["Bomet Central", "Bomet East", "Chepalungu", "Konoin", "Sotik"],
  Kakamega: [
    "Butere",
    "Kakamega Central",
    "Kakamega East",
    "Kakamega North",
    "Kakamega South",
    "Khwisero",
    "Lugari",
    "Lukuyani",
    "Lurambi",
    "Matete",
    "Mumias",
    "Mutungu",
    "Navakholo",
  ],
  Vihiga: ["Emuhaya", "Hamisi", "Luanda", "Sabatia", "Vihiga"],
  Bungoma: [
    "Bumula",
    "Kabuchai",
    "Kanduyi",
    "Kimilili",
    "Mt Elgon",
    "Sirisia",
    "Tongaren",
    "Webuye East",
    "Webuye West",
  ],
  Busia: [
    "Budalangi",
    "Butula",
    "Funyula",
    "Nambele",
    "Teso North",
    "Teso South",
  ],
  Siaya: ["Alego Usonga", "Bondo", "Gem", "Rarieda", "Ugenya", "Unguja"],
  Kisumu: [
    "Kisumu Central",
    "Kisumu East",
    "Kisumu West",
    "Muhoroni",
    "Nyakach",
    "Nyando",
    "Seme",
  ],
  "Homa Bay": [
    "Homa Bay Town",
    "Kabondo",
    "Karachwonyo",
    "Kasipul",
    "Mbita",
    "Ndhiwa",
    "Rangwe",
    "Suba",
  ],
  Migori: [
    "Awendo",
    "Kuria East",
    "Kuria West",
    "Mabera",
    "Ntimaru",
    "Rongo",
    "Suna East",
    "Suna West",
    "Uriri",
  ],
  Kisii: [
    "Bonchari",
    "Bomachoge Borabu",
    "Bomachoge Chache",
    "Kitutu Chache North",
    "Kitutu Chache South",
    "Nyaribari Chache",
    "Nyaribari Masaba",
    "South Mugirango",
  ],
  Nyamira: [
    "Borabu",
    "Manga",
    "Masaba North",
    "Nyamira North",
    "Nyamira South",
  ],
  Nairobi: [
    "Dagoretti North",
    "Dagoretti South",
    "Embakasi Central",
    "Embakasi East",
    "Embakasi North",
    "Embakasi South",
    "Embakasi West",
    "Kamukunji",
    "Kasarani",
    "Kibra",
    "Lang'ata",
    "Makadara",
    "Mathare",
    "Roysambu",
    "Ruaraka",
    "Starehe",
    "Westlands",
  ],
};

// County coordinates (approximate center points)
const countyCoordinates = {
  Mombasa: [39.6645, -4.0435],
  Kwale: [39.3091, -4.1734],
  Kilifi: [39.9093, -3.6305],
  "Tana River": [39.3735, -1.5628],
  Lamu: [40.902, -2.2717],
  "Taita-Taveta": [38.3454, -3.3161],
  Garissa: [39.6367, -0.4563],
  Wajir: [40.0573, 1.749],
  Mandera: [40.1159, 3.9373],
  Marsabit: [37.9899, 2.335],
  Isiolo: [38.485, 0.3546],
  Meru: [37.6559, 0.0515],
  "Tharaka-Nithi": [37.94, -0.2962],
  Embu: [37.4586, -0.5314],
  Kitui: [38.0106, -1.366],
  Machakos: [37.2624, -1.5167],
  Makueni: [37.6203, -2.2515],
  Nyandarua: [36.5229, -0.5531],
  Nyeri: [36.9519, -0.4169],
  Kirinyaga: [37.1208, -0.563],
  "Murang'a": [37.1526, -0.7839],
  Kiambu: [36.8304, -1.1714],
  Turkana: [35.5286, 3.1199],
  "West Pokot": [35.2718, 1.7809],
  Samburu: [37.0862, 1.2179],
  "Trans-Nzoia": [34.9449, 1.0561],
  "Uasin Gishu": [35.3088, 0.5143],
  "Elgeyo-Marakwet": [35.4786, 0.7806],
  Nandi: [35.1118, 0.1836],
  Baringo: [35.9662, 0.4662],
  Laikipia: [36.7819, 0.3606],
  Nakuru: [36.08, -0.3031],
  Narok: [35.8601, -1.0783],
  Kajiado: [36.782, -2.0981],
  Kericho: [35.2863, -0.3677],
  Bomet: [35.3416, -0.7833],
  Kakamega: [34.7526, 0.2827],
  Vihiga: [34.7275, 0.0765],
  Bungoma: [34.5606, 0.5695],
  Busia: [34.0971, 0.4608],
  Siaya: [34.2697, 0.0677],
  Kisumu: [34.7526, -0.0917],
  "Homa Bay": [34.4608, -0.5273],
  Migori: [34.4731, -1.0634],
  Kisii: [34.7741, -0.6773],
  Nyamira: [34.9354, -0.5669],
  Nairobi: [36.8219, -1.2921],
};

// Sub-county coordinates (approximate center points)
const subCountyCoordinates = {
  // Mombasa
  Changamwe: [39.628, -4.0167],
  Jomvu: [39.6933, -4.0435],
  Kisauni: [39.6167, -4.0167],
  Likoni: [39.65, -4.0833],
  Mvita: [39.6833, -4.0167],
  Nyali: [39.6833, -4.0167],

  // Kwale
  Kinango: [39.3091, -4.1734],
  "Lunga Lunga": [39.1167, -4.45],
  Msambweni: [39.4667, -4.4667],
  Matuga: [39.25, -4.1333],

  // Kilifi
  Ganze: [39.5, -3.5333],
  Kaloleni: [39.6167, -3.8167],
  "Kilifi North": [39.85, -3.6333],
  "Kilifi South": [39.85, -3.6333],
  Magarini: [39.9667, -3.0167],
  Malindi: [40.1167, -3.2167],
  Rabai: [39.6167, -3.9167],

  // Nairobi (sample - can be expanded)
  Westlands: [36.7833, -1.2667],
  "Dagoretti North": [36.7667, -1.2833],
  "Dagoretti South": [36.7667, -1.2833],
  "Embakasi Central": [36.9, -1.3167],
  "Embakasi East": [36.9, -1.3167],
  "Embakasi North": [36.9, -1.3167],
  "Embakasi South": [36.9, -1.3167],
  "Embakasi West": [36.9, -1.3167],
  Kamukunji: [36.8333, -1.2833],
  Kasarani: [36.8833, -1.2333],
  Kibra: [36.7833, -1.3167],
  "Lang'ata": [36.7667, -1.3667],
  Makadara: [36.8667, -1.3],
  Mathare: [36.8667, -1.2833],
  Roysambu: [36.8833, -1.2167],
  Ruaraka: [36.8833, -1.25],
  Starehe: [36.8167, -1.2833],

  // Nakuru (sample)
  Bahati: [36.0833, -0.2833],
  Gilgil: [36.3167, -0.4833],
  "Kuresoi North": [35.95, -0.2833],
  "Kuresoi South": [35.95, -0.2833],
  Molo: [35.7333, -0.25],
  Naivasha: [36.4333, -0.7167],
  "Nakuru Town East": [36.0667, -0.2833],
  "Nakuru Town West": [36.0667, -0.2833],
  Njoro: [35.9333, -0.3333],
  Rongai: [35.8667, -0.4],
  Subukia: [36.0833, -0.2],
};

// Function to populate sub-county dropdown based on selected county
function populateSubCounties(countySelectId, subCountySelectId) {
  const countySelect = document.getElementById(countySelectId);
  const subCountySelect = document.getElementById(subCountySelectId);

  if (!countySelect || !subCountySelect) {
    console.error("County or Sub-County select elements not found");
    return;
  }

  // Clear existing sub-county options
  subCountySelect.innerHTML = '<option value="">All Sub-Counties</option>';

  const selectedCounty = countySelect.value;

  if (selectedCounty && kenyaLocations[selectedCounty]) {
    // Add sub-counties for the selected county
    kenyaLocations[selectedCounty].forEach((subCounty) => {
      const option = document.createElement("option");
      option.value = subCounty;
      option.textContent = subCounty;
      subCountySelect.appendChild(option);
    });
  }
}

// Function to get all counties as an array
function getAllCounties() {
  return Object.keys(kenyaLocations).sort();
}

// Function to get sub-counties for a specific county
function getSubCountiesForCounty(county) {
  return kenyaLocations[county] || [];
}

// Helper functions for searchable dropdown functionality
function searchCounties(query) {
  if (!query) return getAllCounties();
  const lowerQuery = query.toLowerCase();
  return getAllCounties().filter((county) =>
    county.toLowerCase().includes(lowerQuery)
  );
}

function searchSubCounties(county, query) {
  if (!county) return [];
  const subCounties = getSubCountiesForCounty(county);
  if (!query) return subCounties;
  const lowerQuery = query.toLowerCase();
  return subCounties.filter((subCounty) =>
    subCounty.toLowerCase().includes(lowerQuery)
  );
}

// Expose functions and data to global scope for Alpine.js
window.getAllCounties = getAllCounties;
window.getSubCountiesForCounty = getSubCountiesForCounty;
window.searchCounties = searchCounties;
window.searchSubCounties = searchSubCounties;
window.kenyaLocations = kenyaLocations;
window.countyCoordinates = countyCoordinates;
window.subCountyCoordinates = subCountyCoordinates;

// Geospatial helpers
function toRadians(degrees) {
  return (degrees * Math.PI) / 180;
}

function haversineDistance(lat1, lng1, lat2, lng2) {
  const R = 6371; // Earth radius in km
  const dLat = toRadians(lat2 - lat1);
  const dLng = toRadians(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRadians(lat1)) *
      Math.cos(toRadians(lat2)) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function findNearestEntry(coordinateMap, lat, lng, allowedKeys = null) {
  const entries = allowedKeys
    ? allowedKeys
        .map((key) =>
          coordinateMap[key] ? [key, coordinateMap[key]] : null
        )
        .filter(Boolean)
    : Object.entries(coordinateMap);

  let closest = null;
  let minDistance = Number.POSITIVE_INFINITY;

  entries.forEach(([name, coords]) => {
    if (!Array.isArray(coords) || coords.length !== 2) return;
    const [entryLng, entryLat] = coords;
    const distance = haversineDistance(lat, lng, entryLat, entryLng);
    if (distance < minDistance) {
      minDistance = distance;
      closest = { name, distance };
    }
  });

  return closest;
}

function getNearestCounty(lat, lng) {
  return findNearestEntry(countyCoordinates, lat, lng);
}

function getNearestSubCounty(county, lat, lng) {
  if (!county || !kenyaLocations[county]) return null;
  const subCounties = kenyaLocations[county].filter(
    (subCounty) => subCountyCoordinates[subCounty]
  );
  return findNearestEntry(subCountyCoordinates, lat, lng, subCounties);
}

function formatCoordinatePair(lat, lng) {
  const latHemisphere = lat >= 0 ? "N" : "S";
  const lngHemisphere = lng >= 0 ? "E" : "W";
  return `${Math.abs(lat).toFixed(4)}° ${latHemisphere}, ${Math.abs(lng).toFixed(
    4
  )}° ${lngHemisphere}`;
}

function resolveKenyaLocation(lat, lng, options = {}) {
  const { selectedCounty = null, selectedSubCounty = null } = options;

  const nearestCounty = getNearestCounty(lat, lng);
  const county = selectedCounty || (nearestCounty ? nearestCounty.name : null);

  let subCounty = selectedSubCounty || null;
  if (!subCounty && county) {
    const nearestSub = getNearestSubCounty(county, lat, lng);
    if (nearestSub) {
      subCounty = nearestSub.name;
    }
  }

  const countyLabel = county ? `${county} County` : null;
  const subCountyLabel = subCounty ? `${subCounty} Sub-County` : null;

  const fallbackLabel = [subCountyLabel, countyLabel]
    .filter(Boolean)
    .join(", ");

  return {
    county,
    countyLabel,
    subCounty,
    subCountyLabel,
    nearestCounty: nearestCounty ? nearestCounty.name : null,
    fallbackLabel: fallbackLabel || (nearestCounty ? nearestCounty.name : "Kenya"),
    coordinateLabel: formatCoordinatePair(lat, lng),
  };
}

window.resolveKenyaLocation = resolveKenyaLocation;
window.formatKenyaCoordinatePair = formatCoordinatePair;
window.kenyaLocations = kenyaLocations;
window.getAllCounties = getAllCounties;

// Function to initialize county dropdown
function initializeCountyDropdown(countySelectId, subCountySelectId) {
  const countySelect = document.getElementById(countySelectId);

  if (!countySelect) {
    console.error("County select element not found");
    return;
  }

  // Clear existing options except "All Counties"
  countySelect.innerHTML = '<option value="">All Counties</option>';

  // Add all counties
  getAllCounties().forEach((county) => {
    const option = document.createElement("option");
    option.value = county;
    option.textContent = county;
    countySelect.appendChild(option);
  });

  // Add event listener to county select
  countySelect.addEventListener("change", () => {
    populateSubCounties(countySelectId, subCountySelectId);
  });
}
