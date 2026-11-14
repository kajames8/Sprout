package com.jellyai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Talks to a free AI through Groq's OpenAI-compatible chat API.
 * Uses only the JDK (java.net.http), so no extra Maven dependencies are needed.
 *
 * Get a free key (no credit card) at console.groq.com, then type
 * "/key gsk_..." in the chat box, or set a GROQ_API_KEY environment variable.
 *
 * Free models come and go, so if the model isn't available this tries the
 * others in PREFERRED_MODELS, then asks Groq which models your key can use,
 * and remembers whichever one works.
 *
 * Any OpenAI-compatible service works: change BASE below.
 */
final class AiClient {

    private static final String BASE = "https://api.groq.com/openai/v1";
    private static final int MAX_TOKENS = 600;

    // Tried in this order until one works.
    private static final String[] PREFERRED_MODELS = {
            "llama-3.3-70b-versatile",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "llama-3.1-8b-instant"
    };

    /** One chat message. role is "user" or "assistant". */
    record Turn(String role, String text) { }

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(FloatingPetWindow.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AiClient() { }

    static String getKey() {

        String env = System.getenv("GROQ_API_KEY");

        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        return PREFS.get("api_key", "").trim();
    }

    static boolean hasKey() {
        return !getKey().isEmpty();
    }

    static void saveKey(String key) {
        PREFS.put("api_key", key.trim());
    }

    static void clearKey() {
        PREFS.remove("api_key");
    }

    /**
     * Sends the conversation and returns the reply text. Blocks, so call it
     * from a background thread. Throws an Exception with a readable message
     * if something goes wrong.
     */
    static String chat(String system, List<Turn> history) throws Exception {

        String key = getKey();

        if (key.isEmpty()) {
            throw new IllegalStateException("No API key set.");
        }

        // The model that worked last time goes first.
        List<String> candidates = new ArrayList<>();
        String saved = PREFS.get("ai_model", "");

        if (!saved.isEmpty()) {
            candidates.add(saved);
        }

        for (String model : PREFERRED_MODELS) {
            if (!candidates.contains(model)) {
                candidates.add(model);
            }
        }

        HttpResponse<String> response = null;
        boolean askedForList = false;

        for (int i = 0; i < candidates.size(); i++) {

            String model = candidates.get(i);

            response = send(key, model, system, history);

            if (response.statusCode() == 200) {
                PREFS.put("ai_model", model);
                break;
            }

            // Model missing or not allowed: try the next one.
            boolean modelProblem = response.statusCode() == 404
                    || (response.statusCode() == 400 && mentionsModel(response.body()))
                    || (response.statusCode() == 403 && mentionsModel(response.body()));

            if (!modelProblem) {
                break;
            }

            // Out of preset models: ask Groq what this key can actually use.
            if (i == candidates.size() - 1 && !askedForList) {

                askedForList = true;

                for (String found : listChatModels(key)) {
                    if (!candidates.contains(found)) {
                        candidates.add(found);
                    }
                }
            }
        }

        return readReply(response);
    }

    private static boolean mentionsModel(String body) {
        return body != null && body.toLowerCase().contains("model");
    }

    private static HttpResponse<String> send(String key, String model,
                                             String system, List<Turn> history)
            throws Exception {

        // The system prompt is the first message, with role "system".
        StringBuilder messages = new StringBuilder("[{\"role\":\"system\",\"content\":")
                .append(quote(system)).append('}');

        for (Turn t : history) {

            messages.append(",{\"role\":").append(quote(t.role()))
                    .append(",\"content\":").append(quote(t.text())).append('}');
        }

        messages.append(']');

        String body = "{\"model\":" + quote(model)
                + ",\"max_tokens\":" + MAX_TOKENS
                + ",\"messages\":" + messages + "}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("user-agent", "JellyAI/0.1")
                .header("authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Asks Groq which models this key can use, keeping only chat models.
    private static List<String> listChatModels(String key) {

        List<String> found = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/models"))
                    .timeout(Duration.ofSeconds(20))
                    .header("user-agent", "JellyAI/0.1")
                    .header("authorization", "Bearer " + key)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return found;
            }

            String json = response.body();
            int at = json.indexOf("\"id\"");

            while (at >= 0) {

                String id = extractString(json.substring(at), "id");

                if (id != null && isChatModel(id)) {
                    found.add(id);
                }

                at = json.indexOf("\"id\"", at + 4);
            }

        } catch (Exception e) {
            // Nothing more to try; the caller reports the original error.
        }

        return found;
    }

    private static boolean isChatModel(String id) {

        String s = id.toLowerCase();

        return !(s.contains("whisper") || s.contains("guard") || s.contains("tts")
                || s.contains("orpheus") || s.contains("playai")
                || s.contains("embed") || s.contains("safeguard"));
    }

    private static String readReply(HttpResponse<String> response) {

        if (response.statusCode() == 401) {
            throw new IllegalStateException(
                    "That API key was rejected. Set a new one with /key gsk_...");
        }

        if (response.statusCode() == 429) {
            throw new IllegalStateException(
                    "I'm out of free messages for a moment. Try again in a minute!");
        }

        if (response.statusCode() != 200) {

            String message = extractString(response.body(), "message");

            throw new IllegalStateException("Error " + response.statusCode()
                    + (message == null ? "" : ": " + message));
        }

        // Reply looks like: {"choices":[{"message":{"role":"assistant","content":"..."}}]}
        String text = extractString(response.body(), "content");

        if (text == null || text.isBlank()) {
            throw new IllegalStateException("The reply was empty. Try again!");
        }

        return text.trim();
    }

    // ---------- tiny JSON helpers (enough for this one API) ----------

    private static String quote(String s) {

        StringBuilder out = new StringBuilder("\"");

        for (int i = 0; i < s.length(); i++) {

            char c = s.charAt(i);

            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }

        return out.append('"').toString();
    }

    // Finds the first  "name": "value"  in the JSON and returns the unescaped value.
    private static String extractString(String json, String name) {

        String marker = "\"" + name + "\"";
        int at = json.indexOf(marker);

        while (at >= 0) {

            int i = at + marker.length();

            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }

            if (i < json.length() && json.charAt(i) == ':') {

                i++;

                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }

                if (i < json.length() && json.charAt(i) == '"') {
                    return readString(json, i + 1);
                }
            }

            at = json.indexOf(marker, at + 1);
        }

        return null;
    }

    private static String readString(String json, int start) {

        StringBuilder out = new StringBuilder();

        for (int i = start; i < json.length(); i++) {

            char c = json.charAt(i);

            if (c == '"') {
                return out.toString();
            }

            if (c == '\\' && i + 1 < json.length()) {

                char n = json.charAt(++i);

                switch (n) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            out.append((char) Integer.parseInt(
                                    json.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                        break;
                    default: out.append(n);
                }

            } else {
                out.append(c);
            }
        }

        return out.toString();
    }
}