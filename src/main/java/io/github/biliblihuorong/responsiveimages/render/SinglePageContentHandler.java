package io.github.biliblihuorong.responsiveimages.render;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactiveSinglePageContentHandler;

@Component
public class SinglePageContentHandler implements ReactiveSinglePageContentHandler {

    private final ContentRewriter rewriter;

    public SinglePageContentHandler(ContentRewriter rewriter) {
        this.rewriter = rewriter;
    }

    @Override
    public Mono<SinglePageContentContext> handle(SinglePageContentContext context) {
        return rewriter.rewrite(context.getContent())
            .doOnNext(context::setContent)
            .thenReturn(context);
    }
}
