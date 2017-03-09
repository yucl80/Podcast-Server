package lan.dk.podcastserver.manager.worker.finder;

import javaslang.collection.HashSet;
import lan.dk.podcastserver.entity.Cover;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.service.HtmlService;
import lan.dk.podcastserver.service.ImageService;
import lan.dk.podcastserver.service.JsonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import static java.util.Objects.nonNull;

/**
 * Created by kevin on 20/12/2016 for Podcast Server
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SixPlayFinder implements Finder {

    private final HtmlService htmlService;
    private final ImageService imageService;
    private final JsonService jsonService;

    @Override
    public Podcast find(String url) {
        return htmlService.get(url)
                .map(this::htmlToPodcast)
                .getOrElse(Podcast.DEFAULT_PODCAST);
    }

    private Podcast htmlToPodcast(Document document) {
        return Podcast.builder()
                    .title(document.select("h1.tile-section__title").text())
                    .url(document.select("link[rel=canonical]").attr("href"))
                    .description(getDescription(document.select("script")))
                    .cover(getCover(document.select("div.header-image__image").attr("style")))
                    .type("SixPlay")
                .build();
    }

    private String getDescription(Elements script) {
        return HashSet
                .ofAll(script)
                .find(s -> s.html().contains("root.__6play"))
                .map(Element::html)
                .map(s -> StringUtils.substringBetween(s, "root.__6play = ", "}(this));"))
                .map(jsonService::parse)
                .map(d -> (JSONArray) d.read("context.dispatcher.stores.ProgramStore.programs.*.description"))
                .flatMap(r -> HashSet.ofAll(r).headOption())
                .map(Object::toString)
                .getOrElse(() -> null);
    }

    private Cover getCover(String style) {
        return HashSet
            .of(style.split(";"))
            .find(s -> s.contains("background-image"))
            .map(s -> StringUtils.substringBetween(s, "(", ")"))
            .map(imageService::getCoverFromURL)
            .getOrElse(Cover.DEFAULT_COVER);
    }

    @Override
    public Integer compatibility(@NotEmpty String url) {
        return nonNull(url) && url.contains("www.6play.fr") ? 1 : Integer.MAX_VALUE;
    }
}
