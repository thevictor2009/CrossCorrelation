package ru.the_victor2009.CrossCorrelationWithBot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrossCorrelationWithBot {

	static String[] symbols = { "ETHUSDT", "SOLUSDT", "DOGEUSDT", "XAUUSDT", "CLUSDT", "XAGUSDT", "SKYAIUSDT",
			"XRPUSDT", "1000PEPEUSDT", "ZKJUSDT", "BZUSDT", "ZECUSDT", "AIOTUSDT", "HYPEUSDT", "NAORISUSDT", "ADAUSDT",
			"PENGUUSDT", "DAMUSDT", "ORCAUSDT", "BIOUSDT", "AIGENSYNUSDT", "CHIPUSDT", "SUIUSDT", "TACUSDT", "AVAXUSDT",
			"ZEREBROUSDT", "TAOUSDT", "FILUSDT", "PUMPUSDT", "AAVEUSDT", "PAXGUSDT", "WLFIUSDT", "TRUMPUSDT",
			"LINKUSDT", "ZBTUSDT", "LTCUSDT", "RAVEUSDT", "INTCUSDT", "ENAUSDT", "APEUSDT", "DOTUSDT", "ASTERUSDT",
			"SWARMSUSDT", "CRCLUSDT", "FARTCOINUSDT", "NOMUSDT", "NEARUSDT", "HUSDT", "SOLVUSDT", "WLDUSDT", "APTUSDT",
			"WIFUSDT", "IRUSDT", "RIVERUSDT", "TONUSDT", "PRLUSDT", "BCHUSDT", "MONUSDT", "CHZUSDT", "TRXUSDT",
			"UNIUSDT", "SPKUSDT", "BASEDUSDT", "MOODENGUSDT", "CRVUSDT", "LUMIAUSDT", "MSTRUSDT", "XPLUSDT", "OPGUSDT",
			"SOONUSDT", "API3USDT", "FETUSDT", "VIRTUALUSDT", "KATUSDT", "HOODUSDT", "POLUSDT", "XLMUSDT", "ORDIUSDT",
			"TSLAUSDT", "AXLUSDT", "TRADOORUSDT", "XMRUSDT", "OPUSDT", "MEGAUSDT", "AXSUSDT", "AMZNUSDT", "GOOGLUSDT",
			"ONDOUSDT", "BASUSDT", "NATGASUSDT", "MOVRUSDT", "ETCUSDT", "GENIUSUSDT", "XAUTUSDT", "EDGEUSDT", "LDOUSDT",
			"GWEIUSDT", "ARCUSDT", "SNDKUSDT" };
	static ArrayList<String> symbols2 = new ArrayList<String>();
	private static final String API_KEY = System.getenv("BINANCE_API_KEY");
	private static final String SECRET_KEY = System.getenv("BINANCE_SECRET_KEY");
	private static final String BASE_URL = "https://fapi.binance.com";
	public static final String PERIOD = "5m";
	public static final int maxLag = 20;

	static String currentTradePairX = null;
	static String currentTradePairY = null;
	static int currentLag = 0;
	static double currentCorr = 0;

	static final double CORR_THRESHOLD = 0.7; // минимальная корреляция для торговли
	// static final double ENTRY_THRESHOLD_PERCENT = 0.01; // % изменения x для
	// входа 0,5
	static double entryThresholdPercent = 0;
	static final double STOP_LOSS_PERCENT = 1.0; // стоп-лосс от цены входа 1,0
	static final double TAKE_PROFIT_PERCENT = 0.5; // тейк-профит 2,0
	static final int MAX_HOLDING_BARS = 10; // максимальное время удержания в свечах

	static boolean inPosition = false;
	static String symbol1 = null;
	static String symbol2 = null;
	static String side = null; // "LONG" или "SHORT"
	static String positionSymbol = null;
	static String positionSide = null;
	static double entryPrice = 0.0;
	static double exitPrice = 0.0;
	static double entryQuantity = 0.0;
	static int barsSinceEntry = 0;

	static boolean pendingOpen = false;
	static int openIteration = 0;
	static String pendingSymbol = null;
	static String pendingSymbol1 = null;
	static int pendingLag = 0;
	static double pendingCorr = 0;
	static double pricePercent = 0;
	static double pendingPricePercent = 0;
	private static TelegramBot telegramBot;

	// k – сдвиг (если k > 0 то y отстаёт от x т.е. x – ведущий).
	// x - symbol1, y - symbol2
	// отслеживаем symbol1, торгуем symbol2

	public static void main(String[] args) throws NumberFormatException, Exception {

		// проблемы с кодировкой
		System.setProperty("file.encoding", "UTF-8");
		PrintStream out = new PrintStream(System.out, true, "UTF-8");
		System.setOut(out);

		Map<String, List<Double>> priceData = new HashMap<>();
		;
		Map<String, List<Double>> priceData2 = new HashMap<>();
		List<CorrelationResults> corResults = new ArrayList<CorrelationResults>();
		CorrelationResults result = null;
		double corCoef = 0;

		// Проверка доступности Binance API
		if (!testConnection()) {
			System.err.println("Нет подключения к Binance API. Проверьте интернет.");
			return;
		}
		// iнициализация telegramBot
		telegramBot = new TelegramBot();
		// Инициализация списка
		for (int i = 0; i < symbols.length; i++) {
			symbols2.add(symbols[i]);
		}

		// Добавить счетчик итераций
		int iteration = 0;
		long currentTime = System.currentTimeMillis();
		long barTime, nextRun, sleepTime;

		while (true) {

			// Проверка доступности Binance API
			if (!testConnection()) {
				currentTime = System.currentTimeMillis();// текущее время
				System.out.println("Нет подключения к Binance API. Проверьте интернет. Ждем 10 мин");
				Thread.sleep(1000 * 60 * 10);// ждем 10 мин
			}
			// вычисляем время до следующей свечи
			currentTime = Instant.ofEpochMilli(getServerTime()).toEpochMilli();
			barTime = 5 * 60 * 1000; // 5 min bar
			nextRun = ((currentTime / barTime) + 1) * barTime;
			sleepTime = nextRun - currentTime + 5000; // +5 sec
			System.out.println("Следующий запуск через: " + (sleepTime / 1000) + " секунд");
			Thread.sleep(sleepTime);

			System.out.println("=== Iteration " + (++iteration) + " ===");
			System.out.println("Memory = " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

			try {
				if (!inPosition) {
					if (!inPosition && pendingOpen && iteration >= openIteration) {
						// Наступило время открытия — выполняем вход по pendingSymbol
						String side;
						if (pendingCorr > 0) {
							side = (pendingPricePercent > 0) ? "BUY" : "SELL";
						} else {
							side = (pendingPricePercent > 0) ? "SELL" : "BUY";
						}
						double priceNow = Double.parseDouble(getCupPrice(pendingSymbol, side));
						double quantity = getMinVolume(pendingSymbol);
						// sendOrder(pendingSymbol, side, quantity);
						System.out.println("Enter the position");

						inPosition = true;
						positionSymbol = pendingSymbol;
						positionSide = side.equals("BUY") ? "LONG" : "SHORT";
						entryPrice = priceNow;
						entryQuantity = quantity;
						barsSinceEntry = 0;
						symbol1 = pendingSymbol1;
						symbol2 = pendingSymbol;
						long entryTime = getServerTime();
						pendingOpen = false; // сбрасываем ожидание
						if (telegramBot != null) {
							telegramBot.sendTradeOpen(symbol1, symbol2, positionSide, entryPrice, entryQuantity,
									entryPrice * entryQuantity, Instant.ofEpochMilli(entryTime).toString());
							continue;
						}
					}
					if (!inPosition && !pendingOpen) {
						priceData = getHistoricalData2(symbols2, PERIOD, 300);
						System.out.println("Получили priceData");
						corResults = findCorrelationResults(priceData, maxLag);
						System.out.println("Рассчитали corResults");
						result = filterCorrelationResults(corResults);
						if (result == null) {
							System.out.println("Нет подходящих корреляций, пропускаем итерацию");
							continue;
						}
						System.out.println("result.symbol1= " + result.symbol1 + " result.symbol2= " + result.symbol2);

						Map.Entry<Integer, Double> entry = result.lagCorCoefficient.entrySet().iterator().next();
						currentLag = entry.getKey();
						corCoef = entry.getValue();
						System.out.println("currentLag = " + currentLag + " corCoef = " + corCoef);

						if (Math.abs(corCoef) >= CORR_THRESHOLD) {
							List<Double> prices = priceData.get(result.symbol1);
							pricePercent = calculatePercentChange(prices);
							entryThresholdPercent = calculateVolatility(prices, 250) * 0.5;
							System.out.println("entryThresholdPercent = " + entryThresholdPercent);
							System.out.println("pricePercent = " + pricePercent);

							if (Math.abs(pricePercent) >= entryThresholdPercent) {// ENTRY_THRESHOLD_PERCENT
								// Сигнал получен, но ждём currentLag свечей
								pendingOpen = true;
								openIteration = iteration + currentLag;
								pendingSymbol1 = result.symbol1;
								pendingSymbol = result.symbol2; // торгуем symbol2
								pendingLag = currentLag;
								pendingCorr = corCoef;
								pendingPricePercent = pricePercent;

								System.out.println(
										"Signal detected on " + result.symbol1 + ", will open " + result.symbol2
												+ " after " + currentLag + " bars (iteration " + openIteration + ")");
								// не открываем сразу, выходим из итерации
								continue;
							}
						}
					}
				}
				if (inPosition) {
					// закрываем по истечении currentLog баров
					barsSinceEntry++;
					if (barsSinceEntry >= currentLag) {
						List<Double> prices = priceData.get(symbol2);
						pricePercent = calculatePercentChange(prices);
						if (positionSide.equals("LONG")) {
							// closePositionMarket(symbol2, positionSide, entryQuantity);
							System.out.println("Exit the position");
							double priceNow = Double
									.parseDouble(getCupPrice(symbol2, positionSide.equals("LONG") ? "SELL" : "BUY"));
							exitPrice = priceNow;
							double profit = profit(entryPrice, exitPrice, entryQuantity, positionSide);
							long exitTime = getServerTime();
							inPosition = false;
							if (telegramBot != null) {
								telegramBot.sendTradeClose(symbol2, profit, exitPrice,
										Instant.ofEpochMilli(exitTime).toString());
								continue;
							}
						}
						if (positionSide.equals("SHORT")) {
							// closePositionMarket(symbol2, positionSide, entryQuantity);
							System.out.println("Exit the position");
							double priceNow = Double
									.parseDouble(getCupPrice(symbol2, positionSide.equals("LONG") ? "SELL" : "BUY"));
							exitPrice = priceNow;
							double profit = profit(entryPrice, exitPrice, entryQuantity, positionSide);
							long exitTime = getServerTime();
							inPosition = false;
							if (telegramBot != null) {
								telegramBot.sendTradeClose(symbol2, profit, exitPrice,
										Instant.ofEpochMilli(exitTime).toString());

								continue;
							}
						}
					}
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

	}

	public static double calculateVolatility(List<Double> prices, int period) {
		if (prices == null || prices.size() < period + 1) {
			return 0.2; // защитное значение: 0.2% (чтобы порог не был нулевым)
		}
		List<Double> returns = new ArrayList<>();
		for (int i = prices.size() - period; i < prices.size(); i++) {
			double ret = (prices.get(i) - prices.get(i - 1)) / prices.get(i - 1);
			returns.add(ret);
		}
		double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double variance = returns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).average().orElse(0.0);
		return Math.sqrt(variance) * 100; // переводим в проценты, как pricePercent
	}

	// получение предыдущего значения symbol1 B %
	public static double calculatePercentChange(List<Double> prices) {
		if (prices == null || prices.size() < 2)
			return 0.0;
		double oldPrice = prices.get(prices.size() - 2);
		double newPrice = prices.get(prices.size() - 1);
		return ((newPrice - oldPrice) / oldPrice) * 100;
	}

	// Фильтрация корреляции
	public static CorrelationResults filterCorrelationResults(List<CorrelationResults> allResults) {
		List<CorrelationResults> candidates = new ArrayList<>();

		for (CorrelationResults cr : allResults) {
			double maxCorr = -1.0;
			int bestLag = 0;
			for (Map.Entry<Integer, Double> entry : cr.lagCorCoefficient.entrySet()) {
				if (entry.getValue() > maxCorr) {
					maxCorr = entry.getValue();
					bestLag = entry.getKey();
				}
			}
			// Игнорируем артефактные корреляции, практически равные 1.0 или -1.0
			if (Math.abs(maxCorr) > 0.999) {
				System.out.println("Ignoring pair " + cr.symbol1 + "-" + cr.symbol2 + " with corr=" + maxCorr);
				continue;
			}
			Map<Integer, Double> bestMap = new HashMap<>();
			bestMap.put(bestLag, maxCorr);
			candidates.add(new CorrelationResults(cr.symbol1, cr.symbol2, bestMap));
		}

		if (candidates.isEmpty()) {
			return null;
		}

		double maxValue = -1.0;
		CorrelationResults bestResult = null;
		for (CorrelationResults cr : candidates) {
			Double value = cr.lagCorCoefficient.values().iterator().next();
			if (value > maxValue) {
				maxValue = value;
				bestResult = cr;
			}
		}
		return bestResult;
	}

	// Вычисление корреляции для конкретного лага к и 2 рядов
	public static double calculateOneLagCorrelation(String symbol1, String symbol2, Map<String, List<Double>> priceData,
			Integer lag) {
		List<Double> prices1 = priceData.get(symbol1);
		List<Double> prices2 = priceData.get(symbol2);
		double[] prices1Array = prices1.stream().mapToDouble(Double::doubleValue).toArray();
		double[] prices2Array = prices2.stream().mapToDouble(Double::doubleValue).toArray();
		int n = Math.min(prices1Array.length, prices2Array.length);
		// Средние значения для рядов
		double meanX = mean(prices1Array);
		double meanY = mean(prices2Array);
		// Вычисляем знаменатель (корень из произведения сумм квадратов)
		double sumSqX = 0.0, sumSqY = 0.0;

		for (int a = 0; a < n; a++) {
			double dx = prices1Array[a] - meanX;
			double dy = prices2Array[a] - meanY;
			sumSqX += dx * dx;
			sumSqY += dy * dy;
		}
		double denom = Math.sqrt(sumSqX * sumSqY);
		if (denom == 0) {
			System.out.println("Деление на ноль");
			return 0.0;
		}
		int pairs = n - lag;
		double numerator = 0.0;
		for (int i = 0; i < pairs; i++) {
			double dx = prices1Array[i] - meanX;
			double dy = prices2Array[i + lag] - meanY;
			numerator += dx * dy;
		}
		return numerator / denom;
	}

	// Вычисление корреляции
	public static List<CorrelationResults> findCorrelationResults(Map<String, List<Double>> priceData, int maxLag)
			throws Exception {
		List<CorrelationResults> allResults = new ArrayList<>();
		String[] symbols = priceData.keySet().toArray(new String[0]);

		for (int i = 0; i < symbols.length; i++) {
			for (int j = i + 1; j < symbols.length; j++) {
				String symbol1 = symbols[i];
				String symbol2 = symbols[j];

				List<Double> prices1List = priceData.get(symbol1);
				List<Double> prices2List = priceData.get(symbol2);

				if (prices1List == null || prices2List == null || prices1List.size() < 2 || prices2List.size() < 2) {
					continue; // недостаточно данных для пары
				}

				double[] x = prices1List.stream().mapToDouble(Double::doubleValue).toArray();
				double[] y = prices2List.stream().mapToDouble(Double::doubleValue).toArray();
				int n = Math.min(x.length, y.length);
				int effectiveMaxLag = Math.min(maxLag, n - 1);

				Map<Integer, Double> lagCorCoefficient = new HashMap<>();

				// Для каждого лага k
				for (int k = 2; k <= effectiveMaxLag; k++) {// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					int pairs = n - k; // количество перекрывающихся пар при данном лаге
					if (pairs < 2)
						continue;

					// Берём только те части рядов, которые участвуют в корреляции:
					// для x — первые pairs элементов, для y — элементы с k до k+pairs-1
					double[] xSub = new double[pairs];
					double[] ySub = new double[pairs];
					for (int t = 0; t < pairs; t++) {
						xSub[t] = x[t];
						ySub[t] = y[t + k];
					}

					// Вычисляем средние значения для подмассивов
					double meanX = mean(xSub);
					double meanY = mean(ySub);

					// Вычисляем суммы квадратов отклонений (знаменатель)
					double sumSqX = 0.0, sumSqY = 0.0;
					for (int t = 0; t < pairs; t++) {
						double dx = xSub[t] - meanX;
						double dy = ySub[t] - meanY;
						sumSqX += dx * dx;
						sumSqY += dy * dy;
					}
					double denom = Math.sqrt(sumSqX * sumSqY);
					if (denom == 0.0) {
						lagCorCoefficient.put(k, 0.0);
						continue;
					}

					// Числитель (ковариация)
					double numerator = 0.0;
					for (int t = 0; t < pairs; t++) {
						double dx = xSub[t] - meanX;
						double dy = ySub[t] - meanY;
						numerator += dx * dy;
					}

					double corr = numerator / denom;
					lagCorCoefficient.put(k, corr);
				}

				if (!lagCorCoefficient.isEmpty()) {
					allResults.add(new CorrelationResults(symbol1, symbol2, lagCorCoefficient));
				}
			}
		}
		return allResults;
	}

	// Вспомогательный метод для расчёта среднего арифметического массива
	private static double mean(double[] arr) {
		if (arr.length == 0)
			return 0.0;
		double sum = 0.0;
		for (double v : arr)
			sum += v;
		return sum / arr.length;
	}

	public static class CorrelationResults {
		String symbol1, symbol2;
		Map<Integer, Double> lagCorCoefficient;

		public CorrelationResults(String symbol1, String symbol2, Map<Integer, Double> lagCorCoefficient) {
			this.symbol1 = symbol1;
			this.symbol2 = symbol2;
			this.lagCorCoefficient = lagCorCoefficient;
		}

	}

	// @SuppressWarnings("deprecation")
	public static boolean testConnection() {
		try {
			URL url = new URL("https://fapi.binance.com/fapi/v1/ping");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(10000); // 10 секунд
			conn.setReadTimeout(10000);
			conn.setRequestMethod("GET");
			int responseCode = conn.getResponseCode();
			return responseCode == 200;
		} catch (Exception e) {
			return false;
		}
	}

	// Поучение цены из стакана для расчета прибыли/убытка
	public static String getCupPrice(String symbol, String side) throws Exception {
		String urlStr = BASE_URL + "/fapi/v1/depth?symbol=" + symbol + "&limit=5";
		URL url = new URL(urlStr);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
		connection.setConnectTimeout(10000); // 10 секунд на соединение
		connection.setReadTimeout(10000); // 10 секунд на чтение

		// Получаем ответ
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		int responseCode = connection.getResponseCode();
		StringBuilder response = new StringBuilder();
		String line;
		double bestPrice = 0;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		JSONObject orderBook = new JSONObject(response.toString());
		JSONArray asks = orderBook.getJSONArray("asks"); // Заявки на продажу
		JSONArray bids = orderBook.getJSONArray("bids"); // Заявки на покупку

		if (responseCode == 418) {
			// IP бан - используем оба метода
			handle418Error(connection);
		}
		if (responseCode == 429) {
			// Получаем Retry-After
			String retryAfter = connection.getHeaderField("Retry-After");
			int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : 10;
			waitSeconds = waitSeconds + 10;
			System.out.println("Rate limited (429). Waiting " + waitSeconds + " seconds...");
			Thread.sleep(waitSeconds * 1000L);
		}

		if (responseCode == 200) {
			if (side.equals("BUY")) {
				bestPrice = asks.getJSONArray(0).getDouble(0);
			} else if (side.equals("SELL")) {
				bestPrice = bids.getJSONArray(0).getDouble(0);
			}
			return Double.toString(bestPrice);
		} else {
			throw new RuntimeException("HTTP error: " + responseCode);
		}
	}

	public static int getLotSize(String symbol) throws Exception {
		String url = "https://fapi.binance.com/fapi/v1/exchangeInfo";

		// @SuppressWarnings("deprecation")
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");

		int responseCode = connection.getResponseCode();
		if (responseCode != 200) {
			throw new RuntimeException("HTTP error code: " + responseCode);
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		JSONObject jsonResponse = new JSONObject(response.toString());
		JSONArray symbols = jsonResponse.getJSONArray("symbols");

		for (int i = 0; i < symbols.length(); i++) {
			JSONObject symbolInfo = symbols.getJSONObject(i);
			if (symbol.equalsIgnoreCase(symbolInfo.getString("symbol"))) {
				JSONArray filters = symbolInfo.getJSONArray("filters");
				// String minNotional = "";
				String lotSize = "";

				for (int j = 0; j < filters.length(); j++) {
					JSONObject filter = filters.getJSONObject(j);
					String filterType = filter.getString("filterType");

					if ("MIN_NOTIONAL".equals(filterType)) {
						// minNotional = filter.getString("notional");
					}
					if ("LOT_SIZE".equals(filterType)) {
						lotSize = filter.getString("minQty");
					}
				}
				// Полчучаем количество цифр после точки в строковой записи
				int number = getDecimalPlaces(lotSize);
				return number;
			}
		}
		return 0;
	}

	// Функция для округления ВВЕРХ до заданной точности
	public static double ceilToPrecision(double value, int stepen) {
		double multiplier = Math.pow(10, stepen);
		return Math.ceil(value * multiplier) / multiplier;
	}

	public static int getDecimalPlaces(String number) {
		String[] parts = number.split("\\.");
		if (parts.length == 1) {
			return 0; // целое число
		}
		return parts[1].length();
	}

	// @SuppressWarnings("deprecation")
	public static Double getMinVolume(String symbol) throws Exception {
		String url = "https://fapi.binance.com/fapi/v1/exchangeInfo";

		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");

		int responseCode = connection.getResponseCode();
		if (responseCode != 200) {
			throw new RuntimeException("HTTP error code: " + responseCode);
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		JSONObject jsonResponse = new JSONObject(response.toString());
		JSONArray symbols = jsonResponse.getJSONArray("symbols");

		for (int i = 0; i < symbols.length(); i++) {
			JSONObject symbolInfo = symbols.getJSONObject(i);
			if (symbol.equalsIgnoreCase(symbolInfo.getString("symbol"))) {
				JSONArray filters = symbolInfo.getJSONArray("filters");
				String minNotional = "";
				String lotSize = "";

				for (int j = 0; j < filters.length(); j++) {
					JSONObject filter = filters.getJSONObject(j);
					String filterType = filter.getString("filterType");

					if ("MIN_NOTIONAL".equals(filterType)) {
						minNotional = filter.getString("notional");
					}
					if ("LOT_SIZE".equals(filterType)) {
						lotSize = filter.getString("minQty");
					}
				}
				// Полчучаем количество цифр после точки в строковой записи
				int number = getDecimalPlaces(lotSize);
				// Получаем текущую цену для расчета минимального объема по номиналу
				double currentPrice = Double.parseDouble(getMomentPrice(symbol));
				// Получаем минимально возможный объем исходя из текущей цены
				double minQtyFromNotional = Double.parseDouble(minNotional) / currentPrice;
				// Округляем значение до нужного количество знаков после точки
				// Вместо Math.round используем округление ВВЕРХ
				double minVolume = ceilToPrecision(minQtyFromNotional, number);
				return minVolume;
			}
		}
		return null;
	}

	public static double profit(double entryPrice, double exitPrice, double quantity, String tradeType) {
		double pnl = 0;
		double comission = entryPrice * quantity * 0.05 / 100 + exitPrice * quantity * 0.05 / 100;
		if (tradeType.equals("LONG")) {
			pnl = exitPrice * quantity - entryPrice * quantity - comission;
		}
		if (tradeType.equals("SHORT")) {
			pnl = entryPrice * quantity - exitPrice * quantity - comission;
		}
		return pnl;
	}

	// Отправка распоряжения на открытие/закрытие сделки
	// @SuppressWarnings("deprecation")
	private static void sendOrder(String symbol, String side, double quantity)
			throws Exception, IOException, InvalidKeyException, NoSuchAlgorithmException {
		// Параметры запроса
		String type = "MARKET";
		long timestamp = getServerTime();
		// Создаем параметры запроса
		String queryString = String.format("symbol=%s&side=%s&type=%s&quantity=%s&timestamp=%d", symbol, side, type,
				quantity, timestamp);
		// Создаем подпись
		String signature = generateSignature(queryString, SECRET_KEY);
		// Добавляем подпись к параметрам
		String signedQueryString = queryString + "&signature=" + signature;
		// Создаем URL
		URL url = new URL(BASE_URL + "/fapi/v1/order?" + signedQueryString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("X-MBX-APIKEY", API_KEY);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		// Читаем ответ
		int responseCode = connection.getResponseCode();
		BufferedReader in;
		if (responseCode >= 200 && responseCode < 300) {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} else {
			in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
		}
		String inputLine;
		StringBuilder response = new StringBuilder();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		System.out.println("responseCode= " + responseCode);
		System.out.println("response body= " + response.toString());
	}

	// @SuppressWarnings("deprecation")
	private static boolean closePositionMarket(String symbol, String side, double quantity) throws Exception {
		long timestamp = getServerTime();

		// Важно: для закрытия позиции используем reduceOnly=true
		String queryString = String.format("symbol=%s&side=%s&type=MARKET&quantity=%s&reduceOnly=true&timestamp=%d",
				symbol, side, quantity, timestamp);

		String signature = generateSignature(queryString, SECRET_KEY);
		String signedQueryString = queryString + "&signature=" + signature;

		URL url = new URL(BASE_URL + "/fapi/v1/order?" + signedQueryString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("X-MBX-APIKEY", API_KEY);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);

		int responseCode = connection.getResponseCode();
		BufferedReader in;
		StringBuilder response = new StringBuilder();

		if (responseCode >= 200 && responseCode < 300) {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} else {
			in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
		}

		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		if (responseCode >= 200 && responseCode < 300) {
			JSONObject jsonResponse = new JSONObject(response.toString());
			System.out.println("Position closed successfully: " + jsonResponse.toString());
			return true;
		} else {
			System.err.println("Failed to close position: " + response.toString());
			return false;
		}
	}

	// Шифрование подписи
	private static String generateSignature(String data, String key)
			throws NoSuchAlgorithmException, InvalidKeyException {
		Mac mac = Mac.getInstance("HmacSHA256");
		SecretKeySpec secreKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
		mac.init(secreKeySpec);
		byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

		// Преобразуем в hex-строку (не Base64)
		StringBuilder hexString = new StringBuilder();
		for (byte b : signature) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1)
				hexString.append('0');
			hexString.append(hex);
		}
		return hexString.toString();
	}

	// Получение текущей цены
	// @SuppressWarnings("deprecation")
	public static String getMomentPrice(String symbol) throws Exception {
		String urlStr = BASE_URL + "/fapi/v1/ticker/price?symbol=" + symbol;
		URL url = new URL(urlStr);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");

		// Получаем ответ
		int responseCode = connection.getResponseCode();

		if (responseCode == 418) {
			// IP бан - используем оба метода
			handle418Error(connection);
		}

		if (responseCode == 429) {
			// Получаем Retry-After
			String retryAfter = connection.getHeaderField("Retry-After");
			int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : 10;
			waitSeconds = waitSeconds + 10;
			System.out.println("Rate limited (429). Waiting " + waitSeconds + " seconds...");
			Thread.sleep(waitSeconds * 1000L);
		}

		if (responseCode == 200) {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(connection.getInputStream());
			return node.get("price").asText();
		} else {
			throw new RuntimeException("HTTP error: " + responseCode);
		}
	}

	private static void handle418Error(HttpURLConnection conn) throws Exception {
		// Способ 1: Читаем заголовок Retry-After
		String retryAfter = conn.getHeaderField("Retry-After");
		if (retryAfter != null) {
			int waitSeconds = Integer.parseInt(retryAfter);
			System.out.println("IP banned. Retry-After: " + waitSeconds + " seconds");
			Thread.sleep(waitSeconds * 1000L);
			return;
		}

		// Способ 2: Читаем timestamp из тела ошибки
		BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		JSONObject error = new JSONObject(response.toString());
		String msg = error.getString("msg");

		// Ищем timestamp в сообщении (формат: "banned until 1744874068494")
		Pattern pattern = Pattern.compile("banned until (\\d+)");
		Matcher matcher = pattern.matcher(msg);

		if (matcher.find()) {
			long banUntil = Long.parseLong(matcher.group(1));
			long currentTime = System.currentTimeMillis();
			long waitMillis = banUntil - currentTime;

			if (waitMillis > 0) {
				System.out.println(
						"IP banned until " + new Date(banUntil) + ". Waiting " + (waitMillis / 1000) + " seconds.");
				Thread.sleep(waitMillis);
			}
		} else {
			// Если ничего не нашли, ждем по умолчанию (из документации: от 2 минут до 3
			// дней)
			System.out.println("IP banned, waiting 5 minutes as fallback...");
			Thread.sleep(300000);
		}
	}

	// Получение серверного (или местного при ошибке) времени
	// @SuppressWarnings("deprecation")
	private static long getServerTime() throws Exception {
		try {
			URL url = new URL(BASE_URL + "/fapi/v1/time");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");

			int responseCode = connection.getResponseCode();
			if (responseCode == 429) {
				// Получаем Retry-After
				String retryAfter = connection.getHeaderField("Retry-After");
				int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : 10;
				waitSeconds = waitSeconds + 10;
				System.out.println("Rate limited (429). Waiting " + waitSeconds + " seconds...");
				Thread.sleep(waitSeconds * 1000L);
			}
			if (responseCode == 200) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					String response = reader.lines().collect(Collectors.joining());
					// Парсим JSON ответ для получения serverTime
					return Long.parseLong(response.split("\"serverTime\":")[1].split("}")[0].trim());
				}
			} else {
				throw new RuntimeException("Failed to get server time. response code" + responseCode);
			}
		} catch (Exception e) {
			System.err.println("Warning: Using local time as fullback due to server time error: " + e.getMessage());
			return System.currentTimeMillis();
		}
	}

	// Получаем исторические данные
	// @SuppressWarnings("deprecation")
	public static Map<String, List<Double>> getHistoricalData(String symbol, String interval, int limit) {
		Map<String, List<Double>> priceData = new HashMap<>();
		HttpURLConnection conn = null;
		try {
			List<Double> candles = new ArrayList<Double>();
			String urlStr = "https://fapi.binance.com/fapi/v1/klines?symbol=" + symbol + "&interval=" + interval
					+ "&limit=" + limit;
			URL url = new URL(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
			conn.setConnectTimeout(10000); // 10 секунд на соединение
			conn.setReadTimeout(10000); // 10 секунд на чтение
			conn.setRequestProperty("Connection", "close"); // Закрывать соединение после запроса

			int responseCode = conn.getResponseCode();

			// Читаем заголовки лимитов
			String usedWeight = conn.getHeaderField("X-MBX-USED-WEIGHT-1M");
			if (usedWeight != null) {
				System.out.println("Used weight this minute: " + usedWeight);
				// Если вес > 900 из 1200 — делаем паузу
				if (Integer.parseInt(usedWeight) > 900) {
					System.out.println("Near limit, waiting 50 seconds...");
					Thread.sleep(50000);
				}
			}

			if (responseCode == 429) {
				// Получаем Retry-After
				String retryAfter = conn.getHeaderField("Retry-After");
				int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : 10;
				waitSeconds = waitSeconds + 10;
				System.out.println("Rate limited (429). Waiting " + waitSeconds + " seconds...");
				Thread.sleep(waitSeconds * 1000L);
			}

			if (responseCode == 418) {
				// IP бан - используем оба метода
				handle418Error(conn);
			}

			if (responseCode != 200) {
				throw new RuntimeException("HTTP error: " + responseCode);
			}

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuilder content = new StringBuilder();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}
			in.close();
			conn.disconnect();
			JSONArray jsonArray = new JSONArray(content.toString());
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONArray candleData = jsonArray.getJSONArray(i);
				double close = candleData.getDouble(4);
				candles.add(close);
			}
			priceData.put(symbol, candles);
			// Задержка чтоб не превысить лимиты API
			Thread.sleep(100);
		} catch (Exception e) {
			System.err.println("Таймаут при получении данных для " + symbol + ": " + e.getMessage());
			return new HashMap<>(); // Возвращаем пустую мапу вместо null
		} finally {
			if (conn != null)
				conn.disconnect();
		}
		return priceData;
	}

	// Добавленный код
	// получение исторических данных
	// @SuppressWarnings("deprecation")
	public static Map<String, List<Double>> getHistoricalData2(ArrayList<String> symbols, String interval, int limit) {
		Map<String, List<Double>> priceData = new HashMap<>();
		HttpURLConnection conn = null;
		for (String symbol : symbols) {
			try {
				List<Double> candles = new ArrayList<Double>();
				String urlStr = "https://fapi.binance.com/fapi/v1/klines?symbol=" + symbol + "&interval=" + interval
						+ "&limit=" + limit;
				URL url = new URL(urlStr);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
				conn.setConnectTimeout(10000); // 10 секунд на соединение
				conn.setReadTimeout(10000); // 10 секунд на чтение
				conn.setRequestProperty("Connection", "close"); // Закрывать соединение после запроса

				int responseCode = conn.getResponseCode();

				// Читаем заголовки лимитов
				String usedWeight = conn.getHeaderField("X-MBX-USED-WEIGHT-1M");
				if (usedWeight != null) {
					System.out.println("Used weight this minute: " + usedWeight);
					// Если вес > 900 из 1200 — делаем паузу
					if (Integer.parseInt(usedWeight) > 900) {
						System.out.println("Near limit, waiting 50 seconds...");
						Thread.sleep(50000);
					}
				}

				if (responseCode == 429) {
					// Получаем Retry-After
					String retryAfter = conn.getHeaderField("Retry-After");
					int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : 10;
					waitSeconds = waitSeconds + 10;
					System.out.println("Rate limited (429). Waiting " + waitSeconds + " seconds...");
					Thread.sleep(waitSeconds * 1000L);
				}

				if (responseCode == 418) {
					// IP бан - используем оба метода
					handle418Error(conn);
				}

				if (responseCode != 200) {
					throw new RuntimeException("HTTP error: " + responseCode);
				}

				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				StringBuilder content = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					content.append(inputLine);
				}
				in.close();
				conn.disconnect();
				JSONArray jsonArray = new JSONArray(content.toString());
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONArray candleData = jsonArray.getJSONArray(i);
					double close = candleData.getDouble(4);
					candles.add(close);
				}
				priceData.put(symbol, candles);
				// Задержка чтоб не превысить лимиты API
				Thread.sleep(100);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (conn != null) {
					conn.disconnect();
				}
			}
		}
		return priceData;
	}

}
