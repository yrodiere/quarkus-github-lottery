package io.quarkus.github.lottery.notification;

import java.time.LocalDate;

public record MarkdownNotification(String username, String topic, LocalDate date, String body) {
}
