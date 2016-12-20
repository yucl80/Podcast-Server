package lan.dk.podcastserver.manager.worker.updater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.TypeRef;
import javaslang.collection.HashSet;
import javaslang.collection.Set;
import javaslang.control.Option;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.service.HtmlService;
import lan.dk.podcastserver.service.ImageService;
import lan.dk.podcastserver.service.JsonService;
import lan.dk.podcastserver.service.SignatureService;
import lan.dk.podcastserver.service.properties.PodcastServerParameters;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import javax.validation.Validator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

import static java.util.Objects.nonNull;

/**
 * Created by kevin on 20/12/2016 for Podcast Server
 */
@Slf4j
@Component("SixPlayUpdater")
public class SixPlayUpdater extends AbstractUpdater {

    private static final DateTimeFormatter DATE_FORMATER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    private static final TypeRef<Set<SixPlayItem>> TYPE_ITEMS = new TypeRef<Set<SixPlayItem>>(){};

    private static final String VIDEOS_SELECTOR = "mainStoreState.video.programVideosBySubCategory.%d.%d";
    private static final String URL_TEMPLATE_PODCAST = "http://www.6play.fr/%s-p_%d/";
    private static final String SUBCAT_SELECTOR = "context.dispatcher.stores.ProgramStore.subcats.%d[0].id";
    private static final String PROGRAM_CODE_SELECTOR = "context.dispatcher.stores.ProgramStore.programs.%d.code";
    private static final String PROGRAM_ID_SELECTOR = "context.dispatcher.stores.NavigationStore.routeNameHistory[0].params.programId";

    private final HtmlService htmlService;
    private final JsonService jsonService;
    private final ImageService imageService;

    protected SixPlayUpdater(PodcastServerParameters podcastServerParameters, SignatureService signatureService, Validator validator, HtmlService htmlService, JsonService jsonService, ImageService imageService) {
        super(podcastServerParameters, signatureService, validator);
        this.htmlService = htmlService;
        this.jsonService = jsonService;
        this.imageService = imageService;
    }

    @Override
    public Set<Item> getItems(Podcast podcast) {
        return htmlService.get(podcast.getUrl())
                .map(d -> this.extractItems(d.select("script")))
                .getOrElse(HashSet::empty);
    }

    private Set<Item> extractItems(Elements script) {
        Option<DocumentContext> root6Play = extractJson(script);

        Integer programId = root6Play
                .map(d -> d.read(PROGRAM_ID_SELECTOR, Integer.class))
                .getOrElseThrow(() -> new RuntimeException("programId not found in root.__6play"));

        Integer subcatId = root6Play
                .map(d -> d.read(String.format(SUBCAT_SELECTOR, programId), Integer.class))
                .getOrElseThrow(() -> new RuntimeException("subcatId not found in root.__6play"));

        String programCode = root6Play
                .map(d -> d.read(String.format(PROGRAM_CODE_SELECTOR, programId), String.class))
                .getOrElseThrow(() -> new RuntimeException("programCode not found in root.__6play"));

        String basePath = String.format(URL_TEMPLATE_PODCAST, programCode, programId);

        return root6Play
                .map(d -> d.read(String.format(VIDEOS_SELECTOR, programId, subcatId), TYPE_ITEMS))
                .map(c -> c.map(s -> this.convertToItem(s, basePath)))
                .getOrElseThrow(() -> new RuntimeException("Error during transformation of json to items"));
    }

    private Item convertToItem(SixPlayItem i, String basePath) {
        return Item.builder()
                    .title(i.getTitle())
                    .pubDate(i.getLastDiffusion())
                    .length(i.getDuration())
                    .url(i.url(basePath))
                    .description(i.description)
                    .cover(imageService.getCoverFromURL(i.cover()))
                .build();
    }

    private Option<DocumentContext> extractJson(Elements elements) {
        return HashSet.ofAll(elements)
                .find(s -> s.html().contains("root.__6play"))
                .map(Element::html)
                .map(s -> StringUtils.substringBetween(s, "root.__6play = ", "}(this));"))
                .map(jsonService::parse);
    }

    @Override
    public String signatureOf(Podcast podcast) {
        return "foo";
    }

    @Override
    public Type type() {
        return new Type("SixPlay", "6Play");
    }

    @Override
    public Integer compatibility(String url) {
        return nonNull(url) && url.startsWith("http://www.6play.fr/") ? 1 : Integer.MAX_VALUE;
    }

    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SixPlayItem{

        @Getter @Setter Set<Image> images;
        @Getter @Setter String code;
        @Getter @Setter String description;
        @Getter @Setter String title;
        @Setter String lastDiffusion; /* 2016-12-18 11:20:00 */
        @Getter @Setter Long duration;
        @Getter @Setter String id;

        ZonedDateTime getLastDiffusion() {
            if (Objects.isNull(lastDiffusion)) {
                return null;
            }

            return ZonedDateTime.of(LocalDateTime.parse(lastDiffusion, DATE_FORMATER), ZoneId.of("Europe/Paris"));
        }

        String url(String basePath) {
            return basePath + code + "-c_" + StringUtils.substringAfter(id, "_");
        }

        String cover() {
            return images.headOption()
                    .map(Image::url)
                    .getOrElse(() -> null);
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Image {

            private static final String domain = "https://images.6play.fr";
            private static final String path = "/v1/images/%s/raw?width=600&height=336&fit=max&quality=60&format=jpeg&interlace=1";
            private static final String salt = "54b55408a530954b553ff79e98";

            @Getter @Setter Integer external_key;

            public String url() {
                String path = String.format(Image.path, external_key);
                return domain + path + "&hash=" + DigestUtils.sha1Hex(path + salt);
            }
        }
    }
}
