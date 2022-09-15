package io.quarkus.github.lottery;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static io.quarkus.github.lottery.MockHelper.mockIssueForLottery;
import static io.quarkus.github.lottery.MockHelper.mockIssueForNotification;
import static io.quarkus.github.lottery.MockHelper.mockPagedIterable;
import static io.quarkus.github.lottery.MockHelper.url;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHIssueQueryBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.github.lottery.config.LotteryConfig;
import io.quarkus.github.lottery.github.GitHubRepositoryRef;
import io.quarkus.github.lottery.github.GitHubService;
import io.quarkus.github.lottery.github.Issue;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests that GitHubService correctly interacts with the GitHub clients.
 */
@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class GitHubServiceTest {

    @Inject
    GitHubService gitHubService;

    @Test
    void listRepositories() throws IOException {
        GitHubRepositoryRef repoRef = new GitHubRepositoryRef(1234L, "quarkusio/quarkus");

        var queryIssuesBuilderMock = Mockito.mock(GHIssueQueryBuilder.ForRepository.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));
        given()
                .github(mocks -> {
                    var applicationClient = mocks.applicationClient();
                    var appMock = mocks.ghObject(GHApp.class, repoRef.installationId());
                    when(applicationClient.getApp()).thenReturn(appMock);

                    var installationMock = mocks.ghObject(GHAppInstallation.class, repoRef.installationId());
                    var installationsMocks = mockPagedIterable(installationMock);
                    when(appMock.listInstallations()).thenReturn(installationsMocks);
                    when(installationMock.getId()).thenReturn(repoRef.installationId());
                    var installationRepositoryMock = Mockito.mock(GHRepository.class);
                    var installationRepositoryMocks = mockPagedIterable(installationRepositoryMock);
                    when(installationMock.listRepositories()).thenReturn(installationRepositoryMocks);
                    when(installationRepositoryMock.getFullName()).thenReturn(repoRef.repositoryName());
                })
                .when(() -> {
                    assertThat(gitHubService.listRepositories())
                            .containsExactlyInAnyOrder(repoRef);
                })
                .then().github(mocks -> {
                    verifyNoMoreInteractions(queryIssuesBuilderMock);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void fetchLotteryConfig() throws IOException {
        GitHubRepositoryRef repoRef = new GitHubRepositoryRef(1234L, "quarkusio/quarkus");

        given()
                .github(mocks -> {
                    var repositoryMock = mocks.repository(repoRef.repositoryName());
                    mocks.configFile(repositoryMock, "quarkus-github-lottery.yaml")
                            .fromString("""
                                    notifications:
                                      createIssues:
                                        repository: "quarkusio/quarkus-lottery-reports"
                                    labels:
                                      needsTriage: "triage/needs-triage"
                                    participants:
                                      - username: "yrodiere"
                                        when: ["MONDAY"]
                                        triage:
                                          maxIssues: 3
                                      - username: "gsmet"
                                        when: ["MONDAY", "WEDNESDAY", "FRIDAY"]
                                        triage:
                                          maxIssues: 10
                                    """);
                })
                .when(() -> {
                    var repo = gitHubService.repository(repoRef);

                    assertThat(repo.fetchLotteryConfig())
                            .isNotEmpty()
                            .get().usingRecursiveComparison().isEqualTo(new LotteryConfig(
                                    new LotteryConfig.NotificationsConfig(
                                            new LotteryConfig.NotificationsConfig.CreateIssuesConfig(
                                                    "quarkusio/quarkus-lottery-reports")),
                                    new LotteryConfig.LabelsConfig("triage/needs-triage"),
                                    List.of(
                                            new LotteryConfig.ParticipantConfig(
                                                    "yrodiere",
                                                    Set.of(DayOfWeek.MONDAY),
                                                    new LotteryConfig.ParticipationConfig(
                                                            3)),
                                            new LotteryConfig.ParticipantConfig(
                                                    "gsmet",
                                                    Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                                                    new LotteryConfig.ParticipationConfig(
                                                            10)))));
                })
                .then().github(mocks -> {
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void issuesWithLabel() throws IOException {
        GitHubRepositoryRef repoRef = new GitHubRepositoryRef(1234L, "quarkusio/quarkus");

        var queryIssuesBuilderMock = Mockito.mock(GHIssueQueryBuilder.ForRepository.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));
        given()
                .github(mocks -> {
                    var repositoryMock = mocks.repository(repoRef.repositoryName());
                    mocks.configFile(repositoryMock, "quarkus-github-lottery.yaml")
                            .fromString("""
                                    notifications:
                                      createIssues:
                                        repository: "quarkusio/quarkus-lottery-reports"
                                    labels:
                                      needsTriage: "triage/needs-triage"
                                    participants:
                                      - username: "yrodiere"
                                        when: ["MONDAY"]
                                        triage:
                                          maxIssues: 3
                                      - username: "gsmet"
                                        when: ["MONDAY", "WEDNESDAY", "FRIDAY"]
                                        triage:
                                          maxIssues: 10
                                    """);

                    when(repositoryMock.queryIssues()).thenReturn(queryIssuesBuilderMock);
                    var issue1Mock = mockIssueForLottery(mocks, 1, "Hibernate ORM works too well");
                    var issue2Mock = mockIssueForLottery(mocks, 3, "Hibernate Search needs Solr support");
                    var issue3Mock = mockIssueForLottery(mocks, 2, "Where can I find documentation?");
                    var issue4Mock = mockIssueForLottery(mocks, 4, "Hibernate ORM works too well");
                    var issuesMocks = mockPagedIterable(issue1Mock, issue2Mock, issue3Mock, issue4Mock);
                    when(queryIssuesBuilderMock.list()).thenReturn(issuesMocks);
                })
                .when(() -> {
                    var repo = gitHubService.repository(repoRef);

                    assertThat(repo.issuesWithLabel("triage/needs-triage"))
                            .toIterable().containsExactly(
                                    new Issue(1, "Hibernate ORM works too well", url(1)),
                                    new Issue(3, "Hibernate Search needs Solr support", url(3)),
                                    new Issue(2, "Where can I find documentation?", url(2)),
                                    new Issue(4, "Hibernate ORM works too well", url(4)));
                })
                .then().github(mocks -> {
                    verify(queryIssuesBuilderMock).state(GHIssueState.OPEN);
                    verify(queryIssuesBuilderMock).sort(GHIssueQueryBuilder.Sort.UPDATED);
                    verify(queryIssuesBuilderMock).direction(GHDirection.DESC);
                    verify(queryIssuesBuilderMock).pageSize(anyInt());
                    verify(queryIssuesBuilderMock).label("triage/needs-triage");
                    verifyNoMoreInteractions(queryIssuesBuilderMock);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void commentOnDedicatedNotificationIssue_notificationIssuesExist() throws IOException {
        var repoRef = new GitHubRepositoryRef(1234L, "quarkusio/quarkus-lottery-reports");

        var queryIssuesBuilderMock = Mockito.mock(GHIssueQueryBuilder.ForRepository.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));
        var issueBuilderMock = Mockito.mock(GHIssueBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));

        given()
                .github(mocks -> {
                    var repositoryMock = mocks.repository(repoRef.repositoryName());

                    when(repositoryMock.queryIssues()).thenReturn(queryIssuesBuilderMock);
                    when(queryIssuesBuilderMock.assignee("yrodiere")).thenReturn(queryIssuesBuilderMock);
                    var issue1Mock = mockIssueForNotification(mocks, 1, "An unrelated issue");
                    var issue2Mock = mockIssueForNotification(mocks, 2,
                            "yrodiere's report for quarkusio/quarkus on 2017-11-01");
                    var issuesMocks = mockPagedIterable(issue1Mock, issue2Mock);
                    when(queryIssuesBuilderMock.list()).thenReturn(issuesMocks);

                    when(repositoryMock.createIssue("yrodiere's report for quarkusio/quarkus on 2017-11-06"))
                            .thenReturn(issueBuilderMock);
                    var issue3Mock = mocks.issue(3);
                    when(issueBuilderMock.create()).thenReturn(issue3Mock);
                })
                .when(() -> {
                    var repo = gitHubService.repository(repoRef);

                    repo.createNotificationIssue("yrodiere", "yrodiere's report for quarkusio/quarkus",
                            LocalDate.of(2017, 11, 6), "Some content");
                })
                .then().github(mocks -> {
                    verify(queryIssuesBuilderMock).assignee("yrodiere");
                    verify(queryIssuesBuilderMock).state(GHIssueState.OPEN);
                    verify(mocks.issue(2)).close();
                    verify(issueBuilderMock).assignee("yrodiere");
                    verify(issueBuilderMock).body("Some content");
                    verifyNoMoreInteractions(queryIssuesBuilderMock, issueBuilderMock);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void commentOnDedicatedNotificationIssue_notificationIssuesDoNotExist() throws IOException {
        var repoRef = new GitHubRepositoryRef(1234L, "quarkusio/quarkus-lottery-reports");
        var queryIssuesBuilderMock = Mockito.mock(GHIssueQueryBuilder.ForRepository.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));
        var issueBuilderMock = Mockito.mock(GHIssueBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));

        given()
                .github(mocks -> {
                    var repositoryMock = mocks.repository(repoRef.repositoryName());

                    when(repositoryMock.queryIssues()).thenReturn(queryIssuesBuilderMock);
                    var issue1Mock = mockIssueForNotification(mocks, 1, "An unrelated issue");
                    var issuesMocks = mockPagedIterable(issue1Mock);
                    when(queryIssuesBuilderMock.list()).thenReturn(issuesMocks);

                    when(repositoryMock.createIssue("yrodiere's report for quarkusio/quarkus on 2017-11-06"))
                            .thenReturn(issueBuilderMock);
                    var issue2Mock = mocks.issue(2);
                    when(issueBuilderMock.create()).thenReturn(issue2Mock);
                })
                .when(() -> {
                    var repo = gitHubService.repository(repoRef);

                    repo.createNotificationIssue("yrodiere", "yrodiere's report for quarkusio/quarkus",
                            LocalDate.of(2017, 11, 6), "Some content");
                })
                .then().github(mocks -> {
                    verify(queryIssuesBuilderMock).assignee("yrodiere");
                    verify(queryIssuesBuilderMock).state(GHIssueState.OPEN);
                    verify(issueBuilderMock).assignee("yrodiere");
                    verify(issueBuilderMock).body("Some content");
                    verifyNoMoreInteractions(queryIssuesBuilderMock, issueBuilderMock);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

};