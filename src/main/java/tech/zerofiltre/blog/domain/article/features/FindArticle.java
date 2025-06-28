package tech.zerofiltre.blog.domain.article.features;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tech.zerofiltre.blog.domain.FinderRequest;
import tech.zerofiltre.blog.domain.Page;
import tech.zerofiltre.blog.domain.article.ArticleProvider;
import tech.zerofiltre.blog.domain.article.ArticleViewProvider;
import tech.zerofiltre.blog.domain.article.model.Article;
import tech.zerofiltre.blog.domain.article.model.ArticleView;
import tech.zerofiltre.blog.domain.error.ForbiddenActionException;
import tech.zerofiltre.blog.domain.error.ResourceNotFoundException;
import tech.zerofiltre.blog.domain.error.UnAuthenticatedActionException;
import tech.zerofiltre.blog.domain.metrics.MetricsProvider;
import tech.zerofiltre.blog.domain.metrics.model.CounterSpecs;
import tech.zerofiltre.blog.domain.user.model.User;


import static tech.zerofiltre.blog.domain.article.model.Status.PUBLISHED;

public class FindArticle {

    public static final String DOTS = "...";
    private final ArticleProvider articleProvider;
    private final MetricsProvider metricsProvider;
    private final ArticleViewProvider articleViewProvider;

    public FindArticle(ArticleProvider articleProvider, MetricsProvider metricsProvider, ArticleViewProvider articleViewProvider) {
        this.articleProvider = articleProvider;
        this.metricsProvider = metricsProvider;
        this.articleViewProvider = articleViewProvider;
    }

    private static boolean isAuthor(User viewer, Article article) {
        return article.getAuthor().getId() == viewer.getId();
    }

    public Article byId(long id, User viewer) throws ResourceNotFoundException {
        Article result = articleProvider.articleOfId(id)
                .orElseThrow(() -> new ResourceNotFoundException("The article with id: " + id + " does not exist", String.valueOf(id)));

        CounterSpecs counterSpecs = new CounterSpecs();
        counterSpecs.setName(CounterSpecs.ZEROFILTRE_ARTICLE_VIEWS);
        counterSpecs.setTags("status", result.getStatus().toString(), "title", result.getTitle(), "user", viewer != null ? viewer.getFullName() : "");
        metricsProvider.incrementCounter(counterSpecs);

        if (PUBLISHED.equals(result.getStatus()) && (viewer == null || !isAuthor(viewer, result))) {
            result.incrementViewsCount();
            result = articleProvider.save(result);
            ArticleView articleView = new ArticleView(viewer, result);
            articleViewProvider.save(articleView);
        }

        if (viewer != null && (isAuthor(viewer, result) || viewer.isAdmin())) return result;
        if (!result.isPremium()) return result;
        if (viewer != null && viewer.isPro()) return result;

        int halfLength = result.getContent().length() / 2;
        String truncated = result.getContent().substring(0, halfLength) + DOTS;
        result.setContent(truncated);
        return result;

    }

    @WithSpan
    public Page<Article> of( @SpanAttribute FinderRequest request) throws ForbiddenActionException, UnAuthenticatedActionException {
        User user = request.getUser();

        //UNAUTHENTICATED USER TRYING TO GET NON PUBLISHED ARTICLES
        if (!PUBLISHED.equals(request.getStatus())
                && user == null
                && !request.isYours()) {
            throw new UnAuthenticatedActionException("The user token might be expired, try to refresh it. ");
        }

        //NON ADMIN USER TRYING TO GET NON PUBLISHED ARTICLES
        if (!PUBLISHED.equals(request.getStatus())
                && (user == null || !user.getRoles().contains("ROLE_ADMIN"))
                && !request.isYours()) {
            throw new ForbiddenActionException("You are not authorize to request articles other than the published ones with this API. " +
                    "Please request with status=published or try /user/* API resources");
        }

        long authorId = request.isYours() ? request.getUser().getId() : 0;
        return articleProvider.articlesOf(request.getPageNumber(), request.getPageSize(), request.getStatus(), authorId, request.getFilter(), request.getTag());

    }
}
