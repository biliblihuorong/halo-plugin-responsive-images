package io.github.biliblihuorong.responsiveimages.render;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactivePostContentHandler;

@Component
public class PostContentHandler implements ReactivePostContentHandler {

    private final ContentRewriter rewriter;

    public PostContentHandler(ContentRewriter rewriter) {
        this.rewriter = rewriter;
    }

    @Override
    public Mono<PostContentContext> handle(PostContentContext context) {
        return rewriter.rewrite(context.getContent())
            .doOnNext(context::setContent)
            .thenReturn(context);
    }
}
