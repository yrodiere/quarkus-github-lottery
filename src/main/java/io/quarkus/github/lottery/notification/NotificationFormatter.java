package io.quarkus.github.lottery.notification;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import io.quarkus.github.lottery.draw.LotteryReport;
import io.quarkus.github.lottery.github.Issue;

@ApplicationScoped
public class NotificationFormatter {

    public MarkdownNotification formatToMarkdown(LotteryReport report) {
        // TODO produce better output, maybe with Qute templates?
        String repoName = report.drawRef().repositoryName();
        String topic = report.username() + "'s report for " + repoName;
        // TODO apply user timezone if possible
        LocalDate date = report.drawRef().instant().atZone(ZoneOffset.UTC).toLocalDate();
        return new MarkdownNotification(report.username(), topic, date,
                "Hey @" + report.username() + ", here's your report for " + repoName + " on " + date + ".\n"
                        + renderCategory("Triage", report.issuesToTriage()));
    }

    private String renderCategory(String title, List<Issue> issues) {
        StringBuilder builder = new StringBuilder("# ").append(title).append('\n');
        if (issues.isEmpty()) {
            builder.append("No issues in this category this time.\n");
        } else {
            builder.append(issues.stream()
                    .map(issue -> issue.url().toString())
                    .collect(Collectors.joining("\n - ", " - ", "\n")));
        }
        return builder.toString();
    }

}
