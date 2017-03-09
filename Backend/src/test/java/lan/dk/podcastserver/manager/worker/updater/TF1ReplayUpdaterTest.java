package lan.dk.podcastserver.manager.worker.updater;

import javaslang.collection.Set;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.service.HtmlService;
import lan.dk.podcastserver.service.ImageService;
import lan.dk.podcastserver.service.JsonService;
import lan.dk.podcastserver.service.SignatureService;
import lan.dk.utils.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * Created by kevin on 21/07/2016.
 */
@RunWith(MockitoJUnitRunner.class)
public class TF1ReplayUpdaterTest {

    private @Mock SignatureService signatureService;
    private @Mock HtmlService htmlService;
    private @Mock ImageService imageService;
    private @Mock JsonService jsonService;
    private @InjectMocks TF1ReplayUpdater updater;

    @Test
    public void should_sign_for_replay() throws IOException, URISyntaxException {
        /* Given */
        Podcast podcast = Podcast.builder().url("http://www.tf1.fr/tf1/19h-live/videos").build();
        when(jsonService.parseUrl(eq("http://www.tf1.fr/ajax/tf1/19h-live/videos?filter=replay"))).then(i -> IOUtils.fileAsJson("/remote/podcast/tf1replay/19h-live.ajax.replay.json"));
        when(htmlService.parse(anyString())).then(i -> IOUtils.stringAsHtml(i.getArgumentAt(0, String.class)));
        when(signatureService.generateMD5Signature(anyString())).then(i -> IOUtils.digest(i.getArgumentAt(0, String.class)));

        /* When */
        String signature = updater.signatureOf(podcast);

        /* Then */
        assertThat(signature).isEqualTo("7f83bdad4764c28504e39bee7ba7d737");
    }

    @Test
    public void should_sign_for_standard_instead_of_replay() throws IOException, URISyntaxException {
        /* Given */
        Podcast podcast = Podcast.builder().url("http://www.tf1.fr/xtra/olive-et-tom/videos").build();
        when(jsonService.parseUrl(eq("http://www.tf1.fr/ajax/xtra/olive-et-tom/videos?filter=replay"))).then(i -> IOUtils.fileAsJson("/remote/podcast/tf1replay/olive-et-tom.ajax.replay.json"));
        when(jsonService.parseUrl(eq("http://www.tf1.fr/ajax/xtra/olive-et-tom/videos?filter=all"))).then(i -> IOUtils.fileAsJson("/remote/podcast/tf1replay/olive-et-tom.ajax.json"));
        when(htmlService.parse(anyString())).then(i -> IOUtils.stringAsHtml(i.getArgumentAt(0, String.class)));
        when(signatureService.generateMD5Signature(anyString())).then(i -> IOUtils.digest(i.getArgumentAt(0, String.class)));

        /* When */
        String signature = updater.signatureOf(podcast);

        /* Then */
        assertThat(signature).isEqualTo("acf0b3a84ae2244194c67078c95a4efe");
    }

    @Test
    public void should_not_get_name_from_url() {
        /* Given */
        Podcast podcast = Podcast.builder().url("http://www.tf1.fr/foo/bar").build();

        /* When */
        String signature = updater.signatureOf(podcast);

        /* Then */
        assertThat(signature).isEmpty();
    }

    @Test
    public void should_get_items() throws IOException, URISyntaxException {
        /* Given */
        Podcast podcast = Podcast.builder().url("http://www.tf1.fr/tf1/19h-live/videos").build();
        when(jsonService.parseUrl(eq("http://www.tf1.fr/ajax/tf1/19h-live/videos?filter=replay"))).then(i -> IOUtils.fileAsJson("/remote/podcast/tf1replay/19h-live.ajax.replay.json"));
        when(htmlService.parse(anyString())).then(i -> IOUtils.stringAsHtml(i.getArgumentAt(0, String.class)));

        /* When */
        Set<Item> items = updater.getItems(podcast);

        /* Then */
        assertThat(items).hasSize(8);
    }

    @Test
    public void should_be_of_type() {
        assertThat(updater.type().key()).isEqualTo("TF1Replay");
        assertThat(updater.type().name()).isEqualTo("TF1 Replay");
    }

    @Test
    public void should_be_compatible() {
        /* Given */
        String url = "www.tf1.fr/tf1/19h-live/videos";
        /* When */
        Integer compatibility = updater.compatibility(url);
        /* Then */
        assertThat(compatibility).isEqualTo(1);
    }

    @Test
    public void should_not_be_compatible() {
        /* Given */
        String url = "www.tf1.com/foo/bar/videos";
        /* When */
        Integer compatibility = updater.compatibility(url);
        /* Then */
        assertThat(compatibility).isEqualTo(Integer.MAX_VALUE);
    }
}