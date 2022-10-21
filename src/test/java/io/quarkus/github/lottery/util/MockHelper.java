package io.quarkus.github.lottery.util;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import io.quarkiverse.githubapp.testing.dsl.GitHubMockContext;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Answers;
import org.mockito.quality.Strictness;

public class MockHelper {

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> PagedSearchIterable<T> mockPagedIterable(T... contentMocks) {
        PagedSearchIterable<T> iterableMock = mock(PagedSearchIterable.class,
                withSettings().stubOnly().strictness(Strictness.LENIENT).defaultAnswer(Answers.RETURNS_SELF));
        when(iterableMock.spliterator()).thenAnswer(ignored -> List.of(contentMocks).spliterator());
        when(iterableMock.iterator()).thenAnswer(ignored -> {
            PagedIterator<T> iteratorMock = mock(PagedIterator.class, withSettings().stubOnly().strictness(Strictness.LENIENT));
            Iterator<T> actualIterator = List.of(contentMocks).iterator();
            when(iteratorMock.next()).thenAnswer(ignored2 -> actualIterator.next());
            when(iteratorMock.hasNext()).thenAnswer(ignored2 -> actualIterator.hasNext());
            return iteratorMock;
        });
        return iterableMock;
    }

    public static URL url(int id) {
        try {
            return new URL("http://github.com/quarkusio/quarkus/issues/" + id);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static GHIssue mockIssueForLottery(GitHubMockContext context, int number, String title, Date updatedAt)
            throws IOException {
        GHIssue mock = context.issue(10000L + number);
        when(mock.getNumber()).thenReturn(number);
        when(mock.getTitle()).thenReturn(title);
        when(mock.getHtmlUrl()).thenReturn(url(number));
        when(mock.getUpdatedAt()).thenReturn(updatedAt);
        return mock;
    }

    public static GHIssue mockIssueForLotteryFilteredOutByDate(GitHubMockContext context, int number, Date updatedAt)
            throws IOException {
        GHIssue mock = context.issue(10000L + number);
        when(mock.getUpdatedAt()).thenReturn(updatedAt);
        return mock;
    }

    public static GHIssue mockIssueForNotification(GitHubMockContext context, long id, String title) {
        GHIssue mock = context.issue(id);
        when(mock.getTitle()).thenReturn(title);
        return mock;
    }

    public static GHPullRequestFileDetail mockGHPullRequestFileDetail(String filename) {
        GHPullRequestFileDetail mock = mock(GHPullRequestFileDetail.class);
        lenient().when(mock.getFilename()).thenReturn(filename);
        return mock;
    }

}
