/**
 * CoinLab Liquidation Heatmap Engine — Firebase Cloud Functions
 * v12.2 — CoinGlass-grade professional backend
 *
 * This backend processes Binance Futures data and writes
 * pre-computed heatmap data to Firebase Realtime Database.
 * Mobile app reads from RTDB for real-time updates.
 *
 * Functions:
 *   1. processHeatmap (Scheduled every 30s) — Fetches exchange data, computes heatmap
 *   2. onDemandHeatmap (HTTPS Callable) — Instant heatmap for first load
 *   3. cleanupOldData (Scheduled daily) — Purges stale data
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const axios = require("axios");

initializeApp();

// ═══════════════════════════════════════════════════════════════
// Configuration
// ═══════════════════════════════════════════════════════════════

const BINANCE_FUTURES = "https://fapi.binance.com";
const BYBIT_API = "https://api.bybit.com";
const OKX_API = "https://www.okx.com";
const BITGET_API = "https://api.bitget.com";
const GATEIO_API = "https://api.gateio.ws";

const EXCHANGE_WEIGHTS = {
  Binance: 0.35,
  Bybit: 0.25,
  OKX: 0.20,
  Bitget: 0.10,
  "Gate.io": 0.10,
};

const LEVERAGE_TIERS = [
  { leverage: 2, weight: 0.05 },
  { leverage: 3, weight: 0.08 },
  { leverage: 5, weight: 0.15 },
  { leverage: 10, weight: 0.25 },
  { leverage: 20, weight: 0.20 },
  { leverage: 25, weight: 0.10 },
  { leverage: 50, weight: 0.10 },
  { leverage: 75, weight: 0.04 },
  { leverage: 100, weight: 0.02 },
  { leverage: 125, weight: 0.01 },
];

// Symbols to process
const SYMBOLS = ["BTC", "ETH", "SOL", "BNB", "XRP", "DOGE", "ADA", "AVAX", "LINK", "DOT"];
const TIMEFRAMES = ["24H", "48H", "1W", "1M", "3M"];

// ═══════════════════════════════════════════════════════════════
// 1. Scheduled Heatmap Processor (every 30 seconds)
// ═══════════════════════════════════════════════════════════════

exports.processHeatmap = onSchedule(
  {
    schedule: "every 1 minutes",
    timeoutSeconds: 55,
    memory: "512MiB",
    region: "europe-west1",
  },
  async () => {
    const db = getDatabase();
    const now = Date.now();

    // Process top 3 symbols every cycle; rotate others
    const cycleIndex = Math.floor(now / 60000) % Math.ceil(SYMBOLS.length / 3);
    const symbolsToProcess = SYMBOLS.slice(cycleIndex * 3, cycleIndex * 3 + 3);

    for (const symbol of symbolsToProcess) {
      try {
        const data = await computeHeatmapData(symbol, "24H");
        await db.ref(`heatmap/${symbol}/24H`).set(data);
        console.log(`Processed ${symbol}/24H — ${data.buckets.length} buckets`);
      } catch (err) {
        console.error(`Error processing ${symbol}:`, err.message);
      }
    }
  }
);

// ═══════════════════════════════════════════════════════════════
// 2. On-Demand Heatmap (HTTPS Callable)
// ═══════════════════════════════════════════════════════════════

exports.onDemandHeatmap = onCall(
  {
    timeoutSeconds: 30,
    memory: "512MiB",
    region: "europe-west1",
  },
  async (request) => {
    const { symbol, timeframe } = request.data || {};

    if (!symbol || !timeframe) {
      throw new HttpsError("invalid-argument", "symbol and timeframe required");
    }

    const upperSymbol = symbol.toUpperCase();
    const upperTf = timeframe.toUpperCase();

    if (!SYMBOLS.includes(upperSymbol)) {
      throw new HttpsError("invalid-argument", `Unsupported symbol: ${upperSymbol}`);
    }
    if (!TIMEFRAMES.includes(upperTf)) {
      throw new HttpsError("invalid-argument", `Unsupported timeframe: ${upperTf}`);
    }

    // Check cache first
    const db = getDatabase();
    const cached = await db.ref(`heatmap/${upperSymbol}/${upperTf}`).once("value");
    const cachedData = cached.val();

    // Use cache if less than 20 seconds old
    if (cachedData && cachedData.lastUpdated && Date.now() - cachedData.lastUpdated < 20000) {
      return cachedData;
    }

    // Compute fresh data
    const data = await computeHeatmapData(upperSymbol, upperTf);
    await db.ref(`heatmap/${upperSymbol}/${upperTf}`).set(data);

    return data;
  }
);

// ═══════════════════════════════════════════════════════════════
// 3. Daily Cleanup
// ═══════════════════════════════════════════════════════════════

exports.cleanupOldData = onSchedule(
  {
    schedule: "every day 03:00",
    timeoutSeconds: 60,
    region: "europe-west1",
  },
  async () => {
    const db = getDatabase();
    const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;

    for (const symbol of SYMBOLS) {
      for (const tf of TIMEFRAMES) {
        try {
          const snap = await db.ref(`heatmap/${symbol}/${tf}/lastUpdated`).once("value");
          if (snap.val() && snap.val() < cutoff) {
            await db.ref(`heatmap/${symbol}/${tf}`).remove();
            console.log(`Cleaned ${symbol}/${tf}`);
          }
        } catch (e) {
          // ignore
        }
      }
    }
  }
);

// ═══════════════════════════════════════════════════════════════
// Core Computation Engine
// ═══════════════════════════════════════════════════════════════

async function computeHeatmapData(baseCoin, timeframe) {
  const results = await Promise.allSettled([
    fetchBinanceData(baseCoin),
    fetchBybitData(baseCoin),
    fetchOkxData(baseCoin),
    fetchBitgetData(baseCoin),
    fetchGateioData(baseCoin),
    fetchBinanceOrderbook(baseCoin),
  ]);

  const exchanges = results
    .slice(0, 5)
    .filter((r) => r.status === "fulfilled" && r.value)
    .map((r) => r.value);

  const orderbook =
    results[5].status === "fulfilled" ? results[5].value : [];

  if (exchanges.length === 0) {
    return {
      baseCoin,
      timeframe,
      markPrice: 0,
      totalOI: 0,
      fundingRate: 0,
      longRatio: 0.5,
      shortRatio: 0.5,
      exchanges: [],
      buckets: [],
      recentLiquidations: [],
      lastUpdated: Date.now(),
    };
  }

  // Weighted aggregation
  const totalOI = exchanges.reduce((sum, ex) => sum + ex.openInterestUsd, 0);
  const weightedFunding = exchanges.reduce((sum, ex) => {
    const w = EXCHANGE_WEIGHTS[ex.exchange] || 0.1;
    return sum + ex.fundingRate * w;
  }, 0);
  const weightedLong =
    totalOI > 0
      ? exchanges.reduce((sum, ex) => sum + ex.longRatio * ex.openInterestUsd, 0) / totalOI
      : 0.5;

  const allLiquidations = exchanges
    .flatMap((ex) => ex.recentLiquidations || [])
    .sort((a, b) => b.timestamp - a.timestamp);

  const markPrice =
    exchanges.find((e) => e.exchange === "Binance" && e.markPrice > 0)?.markPrice ||
    exchanges.find((e) => e.markPrice > 0)?.markPrice ||
    0;

  // Build professional heatmap
  const buckets = buildHeatmap(
    exchanges,
    allLiquidations,
    markPrice,
    baseCoin,
    orderbook,
    timeframe
  );

  return {
    baseCoin,
    timeframe,
    markPrice,
    totalOI,
    fundingRate: weightedFunding,
    longRatio: weightedLong,
    shortRatio: 1.0 - weightedLong,
    exchanges: exchanges.map((ex) => ({
      name: ex.exchange,
      oi: ex.openInterestUsd,
      funding: ex.fundingRate,
      markPrice: ex.markPrice,
    })),
    buckets,
    recentLiquidations: allLiquidations.slice(0, 100).map((l) => ({
      exchange: l.exchange,
      price: l.price,
      qty: l.quantity,
      side: l.side,
      time: l.timestamp,
      usd: l.usdValue,
    })),
    lastUpdated: Date.now(),
  };
}

// ═══════════════════════════════════════════════════════════════
// Heatmap Builder — Gaussian Liquidation Density Model
// ═══════════════════════════════════════════════════════════════

function buildHeatmap(exchanges, liquidations, markPrice, baseCoin, orderbook, timeframe) {
  if (markPrice <= 0) return [];

  // Range scales with timeframe
  const baseRange = baseCoin === "BTC" ? 0.12 : 0.15;
  const tfMultiplier = { "24H": 1.0, "48H": 1.2, "1W": 1.8, "1M": 2.5, "3M": 3.5 };
  const rangePercent = baseRange * (tfMultiplier[timeframe] || 1.0);

  const range = markPrice * rangePercent;
  const bucketCount = timeframe === "3M" ? 60 : timeframe === "1M" ? 70 : 80;
  const bucketSize = (range * 2) / bucketCount;
  const minPrice = markPrice - range;

  const totalWeightedOI = exchanges.reduce((sum, ex) => {
    const w = EXCHANGE_WEIGHTS[ex.exchange] || 0.1;
    return sum + w * ex.openInterestUsd;
  }, 0);

  const avgFunding = exchanges.reduce((s, e) => s + e.fundingRate, 0) / exchanges.length;
  const volatilityFactor = 1.0 + Math.abs(avgFunding) * 100.0;

  const results = [];

  for (let i = 0; i < bucketCount; i++) {
    const low = minPrice + i * bucketSize;
    const high = low + bucketSize;
    const mid = (low + high) / 2;
    const priceDistPct = Math.abs(mid - markPrice) / markPrice;

    // Real liquidations in this bucket
    const realLongLiq = liquidations
      .filter((l) => l.side === "LONG" && l.price >= low && l.price < high)
      .reduce((s, l) => s + l.usdValue, 0);
    const realShortLiq = liquidations
      .filter((l) => l.side === "SHORT" && l.price >= low && l.price < high)
      .reduce((s, l) => s + l.usdValue, 0);

    // Estimated liquidations from leverage tiers
    let estLong = 0;
    let estShort = 0;
    for (const tier of LEVERAGE_TIERS) {
      const longLiqPrice = markPrice * (1.0 - 1.0 / tier.leverage);
      const shortLiqPrice = markPrice * (1.0 + 1.0 / tier.leverage);
      const sigma = bucketSize * 2.5;
      const longW = gaussianDensity(Math.abs(mid - longLiqPrice), sigma);
      const shortW = gaussianDensity(Math.abs(mid - shortLiqPrice), sigma);
      const contrib = totalWeightedOI * tier.weight * volatilityFactor;
      estLong += contrib * longW * tier.leverage;
      estShort += contrib * shortW * tier.leverage;
    }

    // Orderbook depth boost
    const depthBoost = orderbook
      .filter((d) => d.price >= low && d.price < high)
      .reduce((s, d) => s + d.usdValue, 0) * 0.1;

    const decay = priceDistPct > 0.001 ? 1.0 / (1.0 + priceDistPct * 5.0) : 1.0;
    const totalLong = realLongLiq + estLong * decay + depthBoost * 0.4;
    const totalShort = realShortLiq + estShort * decay + depthBoost * 0.6;

    results.push({
      priceLow: round(low, 2),
      priceHigh: round(high, 2),
      priceLevel: round(mid, 2),
      longUsd: round(totalLong, 0),
      shortUsd: round(totalShort, 0),
      totalUsd: round((totalLong + totalShort) * decay, 0),
      events: liquidations.filter((l) => l.price >= low && l.price < high).length,
    });
  }

  return results;
}

function gaussianDensity(distance, sigma) {
  if (sigma <= 0) return 0;
  return Math.exp(-(distance * distance) / (2 * sigma * sigma));
}

function round(val, decimals) {
  const f = Math.pow(10, decimals);
  return Math.round(val * f) / f;
}

// ═══════════════════════════════════════════════════════════════
// Exchange Data Fetchers
// ═══════════════════════════════════════════════════════════════

async function fetchBinanceData(baseCoin) {
  const symbol = `${baseCoin}USDT`;
  const [oiRes, fundingRes, premiumRes, lsRes, liqRes] = await Promise.allSettled([
    axios.get(`${BINANCE_FUTURES}/fapi/v1/openInterest`, { params: { symbol }, timeout: 8000 }),
    axios.get(`${BINANCE_FUTURES}/fapi/v1/fundingRate`, { params: { symbol, limit: 1 }, timeout: 8000 }),
    axios.get(`${BINANCE_FUTURES}/fapi/v1/premiumIndex`, { params: { symbol }, timeout: 8000 }),
    axios.get(`${BINANCE_FUTURES}/futures/data/globalLongShortAccountRatio`, {
      params: { symbol, period: "5m", limit: 1 },
      timeout: 8000,
    }),
    axios.get(`${BINANCE_FUTURES}/fapi/v1/allForceOrders`, { params: { symbol, limit: 100 }, timeout: 8000 }),
  ]);

  const premium = premiumRes.status === "fulfilled" ? premiumRes.value.data : {};
  const mp = parseFloat(premium.markPrice) || 0;
  const oi = oiRes.status === "fulfilled" ? parseFloat(oiRes.value.data.openInterest) || 0 : 0;
  const funding =
    fundingRes.status === "fulfilled" ? parseFloat(fundingRes.value.data[0]?.fundingRate) || 0 : 0;
  const ls = lsRes.status === "fulfilled" ? lsRes.value.data[0] : {};
  const liqs =
    liqRes.status === "fulfilled"
      ? liqRes.value.data.map((l) => ({
          exchange: "Binance",
          price: parseFloat(l.averagePrice) || 0,
          quantity: parseFloat(l.executedQty) || 0,
          side: l.side === "BUY" ? "SHORT" : "LONG",
          timestamp: l.time,
          usdValue: (parseFloat(l.averagePrice) || 0) * (parseFloat(l.executedQty) || 0),
        }))
      : [];

  return {
    exchange: "Binance",
    openInterestUsd: oi * mp,
    fundingRate: funding,
    markPrice: mp,
    longRatio: parseFloat(ls.longAccount) || 0.5,
    shortRatio: parseFloat(ls.shortAccount) || 0.5,
    recentLiquidations: liqs,
  };
}

async function fetchBybitData(baseCoin) {
  const symbol = `${baseCoin}USDT`;
  const resp = await axios.get(`${BYBIT_API}/v5/market/tickers`, {
    params: { category: "linear", symbol },
    timeout: 8000,
  });
  const ticker = resp.data?.result?.list?.[0];
  if (!ticker) return null;

  return {
    exchange: "Bybit",
    openInterestUsd: parseFloat(ticker.openInterestValue) || 0,
    fundingRate: parseFloat(ticker.fundingRate) || 0,
    markPrice: parseFloat(ticker.lastPrice) || 0,
    longRatio: 0.5,
    shortRatio: 0.5,
    recentLiquidations: [],
  };
}

async function fetchOkxData(baseCoin) {
  const instId = `${baseCoin}-USDT-SWAP`;
  const [oiRes, fundRes, markRes] = await Promise.allSettled([
    axios.get(`${OKX_API}/api/v5/public/open-interest`, { params: { instType: "SWAP", instId }, timeout: 8000 }),
    axios.get(`${OKX_API}/api/v5/public/funding-rate`, { params: { instId }, timeout: 8000 }),
    axios.get(`${OKX_API}/api/v5/public/mark-price`, { params: { instType: "SWAP", instId }, timeout: 8000 }),
  ]);

  const mp = markRes.status === "fulfilled" ? parseFloat(markRes.value.data.data?.[0]?.markPx) || 0 : 0;
  const oi =
    oiRes.status === "fulfilled" ? parseFloat(oiRes.value.data.data?.[0]?.oi) || 0 : 0;
  const funding =
    fundRes.status === "fulfilled" ? parseFloat(fundRes.value.data.data?.[0]?.fundingRate) || 0 : 0;

  return {
    exchange: "OKX",
    openInterestUsd: oi * mp,
    fundingRate: funding,
    markPrice: mp,
    longRatio: 0.5,
    shortRatio: 0.5,
    recentLiquidations: [],
  };
}

async function fetchBitgetData(baseCoin) {
  const symbol = `${baseCoin}USDT`;
  const [oiRes, tickerRes] = await Promise.allSettled([
    axios.get(`${BITGET_API}/api/v2/mix/market/open-interest`, {
      params: { productType: "USDT-FUTURES", symbol },
      timeout: 8000,
    }),
    axios.get(`${BITGET_API}/api/v2/mix/market/tickers`, { params: { productType: "USDT-FUTURES" }, timeout: 8000 }),
  ]);

  const ticker =
    tickerRes.status === "fulfilled"
      ? tickerRes.value.data.data?.find((t) => t.symbol?.includes(baseCoin))
      : null;
  const mp = parseFloat(ticker?.lastPr) || 0;
  const oi =
    oiRes.status === "fulfilled" ? parseFloat(oiRes.value.data.data?.amount) || 0 : 0;

  return {
    exchange: "Bitget",
    openInterestUsd: oi * mp,
    fundingRate: 0,
    markPrice: mp,
    longRatio: 0.5,
    shortRatio: 0.5,
    recentLiquidations: [],
  };
}

async function fetchGateioData(baseCoin) {
  const contract = `${baseCoin}_USDT`;
  const resp = await axios.get(`${GATEIO_API}/api/v4/futures/usdt/contracts/${contract}`, { timeout: 8000 });
  const info = resp.data;

  return {
    exchange: "Gate.io",
    openInterestUsd: parseFloat(info.open_interest) || 0,
    fundingRate: parseFloat(info.funding_rate) || 0,
    markPrice: parseFloat(info.mark_price) || 0,
    longRatio: 0.5,
    shortRatio: 0.5,
    recentLiquidations: [],
  };
}

async function fetchBinanceOrderbook(baseCoin) {
  const symbol = `${baseCoin}USDT`;
  const resp = await axios.get(`${BINANCE_FUTURES}/fapi/v1/depth`, {
    params: { symbol, limit: 50 },
    timeout: 8000,
  });
  const book = resp.data;
  const result = [];

  for (const bid of book.bids || []) {
    const price = parseFloat(bid[0]) || 0;
    const qty = parseFloat(bid[1]) || 0;
    if (price > 0) result.push({ price, usdValue: price * qty, isBid: true });
  }
  for (const ask of book.asks || []) {
    const price = parseFloat(ask[0]) || 0;
    const qty = parseFloat(ask[1]) || 0;
    if (price > 0) result.push({ price, usdValue: price * qty, isBid: false });
  }

  return result;
}
