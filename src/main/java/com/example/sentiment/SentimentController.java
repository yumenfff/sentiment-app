package com.example.sentiment;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class SentimentController {

    private static final Set<String> POSITIVE = Set.of(
            "good", "great", "excellent", "happy", "love", "awesome", "hello",
            "nice", "wonderful", "best", "amazing", "fantastic", "perfect");
    private static final Set<String> NEGATIVE = Set.of(
            "bad", "terrible", "awful", "sad", "hate", "worst", "horrible",
            "ugly", "poor", "angry", "boring", "disappointing");

    private final MeterRegistry registry;

    public SentimentController(MeterRegistry registry) {
        this.registry = registry;
    }

    // Мок-модель анализа тональности на основе словаря
    @GetMapping("/sentiment")
    public Map<String, String> sentiment(@RequestParam String text) {
        int score = 0;
        for (String word : text.toLowerCase().split("\\W+")) {
            if (POSITIVE.contains(word)) score++;
            if (NEGATIVE.contains(word)) score--;
        }
        String label = score > 0 ? "positive" : score < 0 ? "negative" : "neutral";
        registry.counter("sentiment.requests", "result", label).increment();
        return Map.of("sentiment", label);
    }

    // Показывает, какой под ответил (для демонстрации балансировки)
    @GetMapping("/info")
    public Map<String, String> info() throws Exception {
        return Map.of("pod", InetAddress.getLocalHost().getHostName());
    }

    // Нагрузка на CPU (для демонстрации HPA)
    @GetMapping("/load")
    public Map<String, Object> load(@RequestParam(defaultValue = "200") int ms) {
        long end = System.currentTimeMillis() + ms;
        double x = 0;
        while (System.currentTimeMillis() < end) {
            x += Math.sqrt(Math.random());
        }
        return Map.of("burned_ms", ms);
    }
}
