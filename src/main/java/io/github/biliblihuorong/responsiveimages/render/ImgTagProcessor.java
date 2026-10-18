package io.github.biliblihuorong.responsiveimages.render;

import static org.thymeleaf.templatemode.TemplateMode.HTML;

import io.github.biliblihuorong.responsiveimages.source.SourceRegistry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.ElementNames;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.MatchingElementName;
import reactor.core.publisher.Mono;
import run.halo.app.theme.dialect.ElementTagPostProcessor;

/**
 * 主题模板里的 img 标签（封面、头像、友链图标等）。任何主题都无需改模板。
 */
@Slf4j
@Component
public class ImgTagProcessor implements ElementTagPostProcessor {

    private final MatchingElementName imgName =
        MatchingElementName.forElementName(HTML, ElementNames.forHTMLName("img"));
    private final SourceRegistry registry;

    public ImgTagProcessor(SourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<IProcessableElementTag> process(ITemplateContext context, IProcessableElementTag tag) {
        if (!imgName.matches(tag.getElementDefinition().getElementName()) || tag.hasAttribute("srcset")) {
            return Mono.empty();
        }
        return registry.snapshot()
            .filter(snapshot -> snapshot.basic().isEnableTemplate() && !snapshot.sources().isEmpty())
            .flatMap(snapshot -> Mono.justOrEmpty(ImgRewriter.candidate(tag::getAttributeValue, snapshot.basic()))
                .filter(c -> registry.supports(snapshot, c.url()))
                .flatMap(c -> registry.resolve(snapshot, Set.of(c.url())).mapNotNull(found -> {
                    var variants = found.get(c.url());
                    if (variants == null) {
                        return null;
                    }
                    var attrs = ImgRewriter.attributes(c, variants, tag::getAttributeValue, snapshot.basic());
                    if (attrs.isEmpty()) {
                        return null;
                    }
                    var factory = context.getModelFactory();
                    IProcessableElementTag newTag = tag;
                    for (var e : attrs.entrySet()) {
                        newTag = factory.setAttribute(newTag, e.getKey(), e.getValue());
                    }
                    return newTag;
                })))
            .onErrorResume(e -> {
                log.warn("Failed to add responsive images to a template image: {}", e.toString());
                return Mono.empty();
            });
    }
}
