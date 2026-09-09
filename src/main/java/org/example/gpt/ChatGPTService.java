package org.example.gpt;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.models.PollOption;
import org.example.models.PollQuestion;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ChatGPTService {

    private String apiKey; // This now holds the token for the new service
    private final HttpClient httpClient;
    private final Gson gson;

    public ChatGPTService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public List<PollQuestion> generatePollQuestions(String topic) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_TOKEN")) {
            throw new IllegalStateException("API Token is not set. Please provide it.");
        }

        String prompt = "Generate 1 to 3 poll questions about '" + topic + "'. " +
                        "Each question should have 2 to 4 options. " +
                        "Return the response as a JSON array where each object has a 'question' (string) and 'options' (array of strings). " +
                        "Example: [{'question': 'Favorite color?', 'options': ['Red', 'Blue']}]";

        String encodedText = URLEncoder.encode(prompt, StandardCharsets.UTF_8);

        String urlString = String.format(
                "https://shaitest-production-3066.up.railway.app/api-request?token=%s&text=%s",
                apiKey,
                encodedText
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60)) // Increased timeout for potentially slow API
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status code " + response.statusCode() + ": " + response.body());
        }

        String responseBody = response.body();

        // 1. Parse the outer JSON object
        JsonObject outerObject = JsonParser.parseString(responseBody).getAsJsonObject();

        // Check for error field if needed
        boolean error = outerObject.get("error").getAsBoolean();
        if (error) {
            throw new IOException("API returned an error: " + outerObject.get("value").getAsString());
        }

        // 2. Extract the string from the "value" field
        String valueString = outerObject.get("value").getAsString();

        // 3. Strip markdown wrappers and extra whitespace
        String cleanedJson = valueString
                .replace("```json", "")
                .replace("```", "")
                .trim();

        int startIndex = cleanedJson.indexOf('[');
        int endIndex = cleanedJson.lastIndexOf(']');
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleanedJson = cleanedJson.substring(startIndex, endIndex + 1);
        }

        // 4. Parse the cleaned JSON array string
        return parseJsonToPollQuestions(cleanedJson);
    }

    private List<PollQuestion> parseJsonToPollQuestions(String json) {
        List<PollQuestion> pollQuestions = new ArrayList<>();
        JsonArray questionsArray = JsonParser.parseString(json).getAsJsonArray();

        for (int i = 0; i < questionsArray.size(); i++) {
            JsonObject questionObject = questionsArray.get(i).getAsJsonObject();
            
            // Handle both "question" and "text" fields for flexibility
            String questionText;
            if (questionObject.has("question")) {
                questionText = questionObject.get("question").getAsString();
            } else if (questionObject.has("text")) {
                questionText = questionObject.get("text").getAsString();
            } else {
                continue; // Skip if no valid question field is found
            }
            
            JsonArray optionsArray = questionObject.getAsJsonArray("options");

            List<PollOption> pollOptions = new ArrayList<>();
            for (int j = 0; j < optionsArray.size(); j++) {
                pollOptions.add(new PollOption(optionsArray.get(j).getAsString()));
            }

            if (!pollOptions.isEmpty()) {
                pollQuestions.add(new PollQuestion(questionText, pollOptions));
            }
        }
        return pollQuestions;
    }
}
