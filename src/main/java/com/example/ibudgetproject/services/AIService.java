package com.example.ibudgetproject.services;
/*
import com.example.ibudgetproject.entities.Transactions.SimTransactions;
import com.example.ibudgetproject.entities.User.FinancialKnowledgeLevel;
import com.example.ibudgetproject.entities.User.Tone;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AIService {

    @Value("${gemini.api.key}")
    private String apiKey;


    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation() // Only serialize fields with @Expose
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    public static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(formatter.format(value));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                String dateString = in.nextString();
                return LocalDateTime.parse(dateString, formatter);
            }
        }
    }

    public static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(formatter.format(value));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                String dateString = in.nextString();
                return LocalDate.parse(dateString, formatter);
            }
        }
    }


    public Map<String, Object> analyzeResults(double[] forecastedVolumes) {
        double avg = Arrays.stream(forecastedVolumes).average().orElse(0.0);

        String evaluation;
        if (avg > 500) {
            evaluation = "High transaction volume expected. Consider monitoring for potential overspending.";
        } else if (avg > 200) {
            evaluation = "Moderate transaction volume. Keep an eye on spending trends.";
        } else {
            evaluation = "Low transaction volume. You may need to adjust budget expectations.";
        }

        String prompt = "Analyze the following forecasted transaction volumes and provide insights. " +
                "Explain it like I'm 5 years old and use emojis: " + GSON.toJson(forecastedVolumes) +
                ". Based on trends, offer financial advice (make it short and simple): " + evaluation +
                " Your response must be a valid JSON object in this exact format: {\"message\": \"Insights and advice\"}.";
        String aiResponse = generateMessage(prompt);

        try {
            Type responseType = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> responseMap = GSON.fromJson(aiResponse, responseType);
            String message = responseMap.get("message");

            Map<String, Object> result = new HashMap<>();
            result.put("message", message);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Error processing AI response: " + e.getMessage());
            return result;
        }
    }


    public Map<String, Object> cleanData(List<SimTransactions> transactions) {
        String prompt = "Analyze the following transactions and identify outliers. " +
                "Return a JSON object: {\"outlierIds\": [id1, id2, ...], \"message\": \"Explanation of outliers and tips\"}. " +
                GSON.toJson(transactions);
        String aiResponse = generateMessage(prompt);

        try {
            Type responseType = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> responseMap = GSON.fromJson(aiResponse, responseType);

            List<Double> outlierIdsDouble = (List<Double>) responseMap.get("outlierIds");
            List<Long> outlierIds = outlierIdsDouble.stream().map(Double::longValue).collect(Collectors.toList());

            String message = (String) responseMap.get("message");

            List<SimTransactions> cleanedTransactions = transactions.stream()
                    .filter(t -> !outlierIds.contains(t.getIdTransaction()))
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("cleanedTransactions", cleanedTransactions);
            result.put("message", message);

            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("cleanedTransactions", transactions);
            result.put("message", "Error processing AI response: " + e.getMessage());
            return result;
        }
    }


    public Map<String, Object> analyzeTransactionParameters(List<SimTransactions> transactions) {
        String prompt = "Analyze the following transactions and predict future averages and standard deviations. " +
                "Return a JSON object: {\"average\": number, \"stdDev\": number, \"message\": \"Explanation and tips\"}. " +
                GSON.toJson(transactions);
        String aiResponse = generateMessage(prompt);

        try {
            Type responseType = new TypeToken<Map<String, Double>>() {
            }.getType();
            Map<String, Double> responseMap = GSON.fromJson(aiResponse, responseType);

            double average = responseMap.get("average");
            double stdDev = responseMap.get("stdDev");

            Type responseTypeString = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> responseMapString = GSON.fromJson(aiResponse, responseTypeString);
            String message = responseMapString.get("message");

            Map<String, Object> result = new HashMap<>();
            result.put("average", average);
            result.put("stdDev", stdDev);
            result.put("message", message);

            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("average", 100.0);
            result.put("stdDev", 15.0);
            result.put("message", "Error processing AI response: " + e.getMessage());
            return result;
        }
    }


    public String generateMessage(String prompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            String jsonRequest = GSON.toJson(requestBody);

            HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
            GenericUrl url = new GenericUrl(GEMINI_API_URL + "?key=" + apiKey);
            HttpRequest request = requestFactory.buildPostRequest(url, ByteArrayContent.fromString("application/json", jsonRequest));
            request.setParser(new JsonObjectParser(JSON_FACTORY));

            request.setConnectTimeout(10000);
            request.setReadTimeout(15000);

            HttpResponse response = request.execute();
            String rawResponse = response.parseAsString();
            System.out.println("Raw API Response: " + rawResponse);

            if (response.getStatusCode() != 200) {
                System.err.println("API Error: Status Code: " + response.getStatusCode() + ", Response: " + rawResponse);
                return "API Error: Status Code: " + response.getStatusCode() + ", Response: " + rawResponse;
            }

            return extractGeneratedText(rawResponse);
        } catch (IOException e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }


    private String extractGeneratedText(String jsonResponse) {
        try {
            System.out.println("Raw API Response: " + jsonResponse);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return "API Error: Empty response.";
            }

            // Try to extract a valid JSON substring
            int jsonStart = jsonResponse.indexOf("{");
            int jsonEnd = jsonResponse.lastIndexOf("}") + 1;

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonSubstring = jsonResponse.substring(jsonStart, jsonEnd);

                // Parse JSON safely
                Type responseType = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> responseBody = GSON.fromJson(jsonSubstring, responseType);

                if (responseBody.containsKey("candidates")) {
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");

                    if (!candidates.isEmpty()) {
                        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");

                        if (content != null && content.containsKey("parts")) {
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

                            if (!parts.isEmpty()) {
                                String text = (String) parts.get(0).get("text");
                                
                                // Try to extract JSON from the text if it contains JSON
                                if (text.contains("{") && text.contains("}")) {
                                    int textJsonStart = text.indexOf("{");
                                    int textJsonEnd = text.lastIndexOf("}") + 1;
                                    if (textJsonStart >= 0 && textJsonEnd > textJsonStart) {
                                        return text.substring(textJsonStart, textJsonEnd);
                                    }
                                }
                                
                                return text;
                            }
                        }
                    }
                }
            }

            return "API Error: No valid JSON response from AI.";
        } catch (JsonSyntaxException | ClassCastException e) {
            return "Error parsing AI response: Malformed JSON. " + e.getMessage();
        } catch (Exception e) {
            return "Unexpected error while processing AI response: " + e.getMessage();
        }
    }

}
*/
 