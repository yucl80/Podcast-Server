package lan.dk.podcastserver.manager.worker.updater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javaslang.collection.HashSet;
import javaslang.collection.List;
import javaslang.collection.Set;
import javaslang.control.Option;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.service.*;
import lan.dk.podcastserver.service.properties.PodcastServerParameters;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import javax.validation.Validator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static lan.dk.podcastserver.utils.MatcherExtractor.PatternExtractor;
import static lan.dk.podcastserver.utils.MatcherExtractor.from;


/**
 * Created by kevin on 09/08/2014 for Podcast Server
 */
@Slf4j
@Component("PluzzUpdater")
public class PluzzUpdater extends AbstractUpdater {

    private static final String JSOUP_ITEM_SELECTOR = "#player-memeProgramme";
    private static final String PLUZZ_INFORMATION_URL = "http://webservices.francetelevisions.fr/tools/getInfosOeuvre/v2/?idDiffusion=%s&catalogue=Pluzz";

    private static PatternExtractor ID_PLUZZ_PATTERN = from(Pattern.compile(".*,([0-9]*).html"));
    private static PatternExtractor ID_PLUZZ_MAIN_PAGE_PATTERN = from(Pattern.compile(".*/referentiel_emissions/([^/]*)/.*"));

    private final HtmlService htmlService;
    private final ImageService imageService;
    private final JsonService jsonService;
    private final M3U8Service m3U8Service;

    public PluzzUpdater(PodcastServerParameters podcastServerParameters, SignatureService signatureService, Validator validator, HtmlService htmlService, ImageService imageService, JsonService jsonService, M3U8Service m3U8Service) {
        super(podcastServerParameters, signatureService, validator);
        this.htmlService = htmlService;
        this.imageService = imageService;
        this.jsonService = jsonService;
        this.m3U8Service = m3U8Service;
    }

    public Set<Item> getItems(Podcast podcast) {
        Option<Document> page = htmlService.get(podcast.getUrl());

        return page
            .map(p -> p.select(JSOUP_ITEM_SELECTOR).select("a.row"))
            .map(this::htmlToItems)
            .map(s -> s.add(getCurrentPlayedItem(page.get())))
            .getOrElse(HashSet::empty);
    }

    private Set<Item> htmlToItems(Elements elements) {
        return HashSet.ofAll(elements)
                .map(element -> getPluzzItemByUrl(element.attr("href")));
    }

    private Item getCurrentPlayedItem(Document page) {
        return ID_PLUZZ_MAIN_PAGE_PATTERN
                .on(page.select("meta[name=og:image]").attr("content"))
                .group(1)
                .map(this::getPluzzItemById)
                .getOrElse(Item.DEFAULT_ITEM);
    }

    @Override
    public String signatureOf(Podcast podcast) {
        Option<Document> page = htmlService.get(podcast.getUrl());

        return page
            .map(p -> p.select(JSOUP_ITEM_SELECTOR))
            .map(l -> (l.size() == 0) ? page.get().html() : l.html())
            .map(signatureService::generateMD5Signature)
            .getOrElse(StringUtils.EMPTY);
    }

    private Item getPluzzItemByUrl(String url) {
        return ID_PLUZZ_PATTERN.on(url)
                .group(1)
                .filter(v -> !v.isEmpty())
                .map(this::getPluzzItemById)
                .getOrElse(Item.DEFAULT_ITEM);
    }

    private Item getPluzzItemById(String pluzzId) {
        return jsonService
                .parseUrl(getPluzzJsonInformation(pluzzId))
                .map(d -> d.read("$", PluzzItem.class))
                .map(this::jsonToItem)
                .getOrElse(Item.DEFAULT_ITEM);
    }

    private Item jsonToItem(PluzzItem pluzzItem) {
        return Item.builder()
                    .title( pluzzItem.title() )
                    .description( pluzzItem.getSynopsis() )
                    .pubDate( pluzzItem.pubDate() )
                    .cover( imageService.getCoverFromURL( pluzzItem.coverUrl() ))
                    .url(getPluzzM38uUrl( pluzzItem.getVideos() ))
                .build();
    }

    @SuppressWarnings("unchecked")
    private String getPluzzM38uUrl(List<PluzzItem.Video> videos) {
        return videos
                .toStream()
                .find(PluzzItem.Video::isM3U)
                .map(PluzzItem.Video::getUrl)
                .map(m3U8Service::getM3U8UrlFormMultiStreamFile)
                .getOrElse("");
    }

    private String getPluzzJsonInformation(String pluzzId) {
        return String.format(PLUZZ_INFORMATION_URL, pluzzId);
    }

    @Override
    public Type type() {
        return new Type("Pluzz", "Pluzz");
    }

    @Override
    public Integer compatibility(String url) {
        return StringUtils.contains(url, "pluzz.francetv.fr")
                ? 1
                : Integer.MAX_VALUE;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PluzzItem {

        private static final ZoneId ZONE_ID = ZoneId.of("Europe/Paris");
        private static final String PLUZZ_COVER_BASE_URL = "http://refonte.webservices.francetelevisions.fr%s";

        @Setter private String titre;
        @JsonProperty("sous_titre") @Setter private String sousTitre;
        @Setter @Getter private String synopsis;

        @Setter private String saison;
        @Setter private String episode;
        @Setter private Diffusion diffusion = new Diffusion();
        @Setter private String image;
        @Setter @Getter private List<Video> videos = List.empty();

        String title() {
            String title = titre;
            if (StringUtils.isNotEmpty(saison)) {
                title = title + " - S" + saison;
            }

            if (StringUtils.isNotEmpty(episode)) {
                title = title + "E" + episode;
            }

            if (StringUtils.isNotEmpty(sousTitre)) {
                title = title + " - " + sousTitre;
            }

            return title;
        }

        ZonedDateTime pubDate() {
            if (isNull(diffusion.getTimestamp())) {
                return null;
            }

            return ZonedDateTime.ofInstant(Instant.ofEpochSecond(diffusion.getTimestamp()), ZONE_ID);
        }

        String coverUrl() {
            return String.format(PLUZZ_COVER_BASE_URL, image);
        }


        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Diffusion {
            @Setter @Getter private Long timestamp;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Video {
            @Setter  private String format;
            @Setter @Getter private String url;

            Boolean isM3U() {
                if (isNull(format))
                    return Boolean.FALSE;

                return format.toLowerCase().contains("m3u8");
            }
        }
    }
}
