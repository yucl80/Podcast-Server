package lan.dk.podcastserver.manager.worker.updater;

import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.service.HtmlService;
import lan.dk.podcastserver.service.ImageService;
import lan.dk.podcastserver.service.JsonService;
import lan.dk.podcastserver.service.SignatureService;
import lan.dk.utils.IOUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Created by kevin on 22/12/2016
 */
@RunWith(MockitoJUnitRunner.class)
public class SixPlayUpdaterTest {

    private @Mock SignatureService signatureService;
    private @Mock HtmlService htmlService;
    private @Mock JsonService jsonService;
    private @Mock ImageService imageService;
    private @InjectMocks SixPlayUpdater updater;

    @Test
    public void should_extract_items() throws IOException, URISyntaxException {
        /* Given */
        Podcast turbo = Podcast.builder()
                    .title("Turbo")
                    .url("http://www.6play.fr/gormiti-p_893")
                .build();
        when(htmlService.get(anyString())).thenReturn(IOUtils.fileAsHtml("/remote/podcast/6play/gormiti-p_893.html"));
        when(jsonService.parse(anyString())).then(i -> IOUtils.stringAsJson(i.getArgumentAt(0, String.class)));

        /* When */
        updater.getItems(turbo).forEach(
                System.out::println
        );
        System.out.println(DigestUtils.sha1Hex("/v1/images/180394/raw?width=600&height=336&fit=max&quality=60&format=jpeg&interlace=154b55408a530954b553ff79e98"));
        /* Then */
    }
}