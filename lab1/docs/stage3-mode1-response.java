// Результат генерации в Режиме 1 (задача без контекста проекта)
// Это ИЛЛЮСТРАТИВНЫЙ пример типичного ответа модели на "голый" промпт -
// используется в отчёте для сравнения с Режимом 2.

import java.util.*;
import okhttp3.*;
import com.google.gson.Gson;

public class ReviewAnalyzer {

    private static final String API_KEY = "sk-proj-1234567890abcdef"; // << секрет прямо в коде
    private OkHttpClient client = new OkHttpClient();

    public String analyzeReview(String reviewText) throws Exception {
        String prompt = "Найди проблемы в этом отзыве: " + reviewText;

        RequestBody body = RequestBody.create(
            new Gson().toJson(Map.of(
                "model", "gpt-4",
                "prompt", prompt
            )),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url("https://api.openai.com/v1/completions")
            .header("Authorization", "Bearer " + API_KEY)
            .post(body)
            .build();

        Response response = client.newCall(request).execute();
        return response.body().string(); // возвращаем сырой ответ как есть
    }

    public static void main(String[] args) throws Exception {
        ReviewAnalyzer analyzer = new ReviewAnalyzer();
        String result = analyzer.analyzeReview("Курьер приехал на 3 дня позже");
        System.out.println(result);
    }
}
