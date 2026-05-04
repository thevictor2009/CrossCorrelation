package ru.the_victor2009.CrossCorrelationWithBot;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class TelegramBot {
	
    private static final String BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN");
    private static final String CHAT_ID = System.getenv("TELEGRAM_CHAT_ID");
    private static final String API_URL = "https://api.telegram.org/bot";
    
    private final HttpClient httpClient;
    private boolean enabled;
    
    public TelegramBot() {
        this.httpClient = HttpClient.newHttpClient();
        this.enabled = BOT_TOKEN != null && CHAT_ID != null && !BOT_TOKEN.isEmpty() && !CHAT_ID.isEmpty();
        
        if (enabled) {
            sendMessage("Торговый бот запущен");
        } else {
            System.out.println("Telegram bot disabled: missing tokens");
        }
    }
    

     // Отправляет текстовое сообщение в Telegram
    public boolean sendMessage(String text) {
        if (!enabled) return false;
        
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlString = API_URL + BOT_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + encodedText + 
            		"&parse_mode=HTML";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("Telegram API error: " + response.body());
            }
            return response.statusCode() == 200;
            
        } catch (Exception e) {
            System.err.println("Failed to send Telegram message: " + e.getMessage());
            return false;
        }
    }
    

     // Отправляет сообщение о новой сделке
    public void sendTradeOpen(String symbol1, String symbol2, String positionSide,
                             double entryPrice,
                             double entryQuantity,
                             double summa,
                             String time) {
        
        String message = String.format(
            "<b>NEW TRADE</b>\n" +
            "──────────────\n" +
            "<b>Position direction:</b> %s\n" +
            " <b>symbol1=</b> %s | <b>symbol2=>/b> %s\n" +
            "──────────────\n" +
            "<b>%s</b>\n" +
            " entryPriceB: %.8f\n" +
            " qtyB: %.8f\n" +
            "──────────────\n" +
            " summa: %.2f\n" +
            " time: %s",
            
            positionSide,
            symbol1, symbol2,
            symbol2, entryPrice, entryQuantity,
            summa, time.substring(0, 19).replace("T", " ")
        );
        
        sendMessage(message);
    }
    

     // Отправляет сообщение о текущем currentProfit
    public void sendCurrentProfit(double currentProfit) {
    	String message = String.format(
    			"<b>currentProfit = </b> %.2f USDT\n",
    			currentProfit
    			);
    	sendMessage(message);
    }
    
    //Отправляет сообщение о закрытии сделки
    public void sendTradeClose(String symbol2,
                              double profit,
                              double exitPriceB,
                              String time) {       
        
        String message = String.format(
            "<b> СДЕЛКА ЗАКРЫТА</b>\n" +
            "──────────────\n" +
            "<b>Прибыль:</b> %.4f USDT\n" +
            "──────────────\n" +
            "<b>%s exitPriceB:</b> %.8f\n" +
            "<b> exitTime :</b> %s",
            profit,
            symbol2, exitPriceB,
            time.substring(0, 19).replace("T", " ")
        );
        
        sendMessage(message);
    }
    

    // Отправляет сообщение об ошибке
    public void sendError(String error) {
        sendMessage("<b>ОШИБКА</b>\n" + error);
    }
}