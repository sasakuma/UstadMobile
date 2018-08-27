package com.ustadmobile.lib.contentscrapers;

import com.ustadmobile.lib.contentscrapers.PhetSimulation.IndexPhetContentScraper;
import com.ustadmobile.lib.contentscrapers.PhetSimulation.PhetContentScraper;
import com.ustadmobile.lib.db.entities.OpdsEntryWithRelations;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import static com.ustadmobile.lib.contentscrapers.ScraperConstants.CONTENT_JSON;

public class TestPhetContentScraper {


    private String EN_LOCATION_FILE = "/com/ustadmobile/lib/contentscrapers/phetsimulation/simulation_en.html";
    private String ES_LOCATION_FILE = "/com/ustadmobile/lib/contentscrapers/phetsimulation/simulation_es.html";
    private String HTML_FILE_LOCATION = "/com/ustadmobile/lib/contentscrapers/phetsimulation/phet-html-detail.html";
    private String JAR_FILE_LOCATION = "/com/ustadmobile/lib/contentscrapers/phetsimulation/phet-jar-detail.html";
    private String FLASH_FILE_LOCATION = "/com/ustadmobile/lib/contentscrapers/phetsimulation/phet-flash-detail.html";

    private final String PHET_MAIN_CONTENT = "/com/ustadmobile/lib/contentscrapers/phetsimulation/phet-main-content.html";

    private String SIM_EN = "simulation_en.html";
    private String SIM_ES = "simulation_es.html";


    final Dispatcher dispatcher = new Dispatcher() {


        @Override
        public MockResponse dispatch(RecordedRequest request) throws InterruptedException {

            try {

                if (request.getPath().startsWith("/api/simulation")) {

                    return new MockResponse().setBody(readFile(HTML_FILE_LOCATION));

                } else if (request.getPath().contains(PHET_MAIN_CONTENT)){

                    return new MockResponse().setBody(readFile(PHET_MAIN_CONTENT));

                } else if (request.getPath().equals("/media/simulation_en.html?download")) {

                    MockResponse mock = new MockResponse();
                    mock.setBody(readFile(EN_LOCATION_FILE));
                    mock.addHeader("ETag", "16adca-5717010854ac0");
                    mock.addHeader("Last-Modified","Fri, 20 Jul 2018 15:36:51 GMT");

                    return mock;
                } else if(request.getPath().contains("/media/simulation_es.html?download")){

                    MockResponse mock = new MockResponse();
                    mock.setBody(readFile(ES_LOCATION_FILE));
                    mock.addHeader("ETag", "16adca-5717010854ac0");
                    mock.addHeader("Last-Modified","Fri, 20 Jul 2018 15:36:51 GMT");

                    return mock;
                } else if(request.getPath().contains("flash")){

                    return new MockResponse().setBody(readFile(FLASH_FILE_LOCATION));
                }else if(request.getPath().contains("jar")){

                    return new MockResponse().setBody(readFile(JAR_FILE_LOCATION));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return new MockResponse().setResponseCode(404);
        }
    };

    public Buffer readFile(String location) throws IOException {
        InputStream videoIn = getClass().getResourceAsStream(location);
        BufferedSource source = Okio.buffer(Okio.source(videoIn));
        Buffer buffer = new Buffer();
        source.readAll(buffer);

        return buffer;
    }


    public void AssertAllFiles(File tmpDir, PhetContentScraper scraper) throws IOException {

        File englishLocation = new File(tmpDir, "en");
        Assert.assertTrue("English Folder exists", englishLocation.isDirectory());

        File englishZip = new File(tmpDir, "en.zip");
        Assert.assertTrue("English Zip exists", englishZip.length() > 0);

        File titleDirectory = new File(englishLocation, scraper.getTitle());

        Assert.assertTrue("English Simulation Folder exists", titleDirectory.isDirectory());

        File aboutFile = new File(titleDirectory, ScraperConstants.ABOUT_HTML);
        Assert.assertTrue("About File English Exists",aboutFile.length() > 0);

        File englishSimulation = new File(titleDirectory, SIM_EN);
        Assert.assertTrue("English Simulation exists", englishSimulation.length() > 0);

        File engETag = new File(titleDirectory, ScraperConstants.ETAG_TXT);
        Assert.assertTrue("English ETag exists", engETag.length() > 0);

        File engModified = new File(titleDirectory, ScraperConstants.LAST_MODIFIED_TXT);
        Assert.assertTrue("English Last Modified exists", engModified.length() > 0);

        File spanishDir = new File(tmpDir, "es");
        Assert.assertTrue("Spanish Folder exists",spanishDir.isDirectory());

        File spanishZip = new File(tmpDir, "es.zip");
        Assert.assertTrue("Spanish Zip exists", spanishZip.length() > 0);

        File spanishTitleDirectory = new File(spanishDir, scraper.getTitle());

        File aboutSpanishFile = new File(spanishTitleDirectory, ScraperConstants.ABOUT_HTML);
        Assert.assertTrue("About File English Exists",aboutSpanishFile.length() > 0);

        File spanishSimulation = new File(spanishTitleDirectory, SIM_ES);
        Assert.assertTrue("Spanish Simulation exists", spanishSimulation.length() > 0);

        File spanishETag = new File(titleDirectory, ScraperConstants.ETAG_TXT);
        Assert.assertTrue("Spanish ETag exists", spanishETag.length() > 0);

        File spanishModified = new File(spanishTitleDirectory, ScraperConstants.LAST_MODIFIED_TXT);
        Assert.assertTrue("Spanish Last Modified exists", spanishModified.length() > 0);

    }


   @Test
    public void givenServerOnline_whenPhetContentScraped_thenShouldConvertAndDownload() throws IOException {
        File tmpDir = Files.createTempDirectory("testphetcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String mockServerUrl = mockWebServer.url("/api/simulation/equality-explorer-two-variables").toString();
        PhetContentScraper scraper = new PhetContentScraper(mockServerUrl, tmpDir);
        scraper.scrapContent();

        AssertAllFiles(tmpDir, scraper);
    }

    @Test
    public void givenServerOnline_whenPhetContentScrapedAgain_thenShouldNotDownloadFilesAgain() throws IOException {
        File tmpDir = Files.createTempDirectory("testphetcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        String mockServerUrl = mockWebServer.url("/api/simulation/equality-explorer-two-variables").toString();
        PhetContentScraper scraper = new PhetContentScraper(mockServerUrl, tmpDir);
        scraper.scrapContent();
        File englishLocation = new File(tmpDir, "en");
        File titleDirectory = new File(englishLocation, scraper.getTitle());
        File englishSimulation = new File(titleDirectory, SIM_EN);

        long firstSimDownload = englishSimulation.lastModified();

        scraper.scrapContent();

        long lastModified = englishSimulation.lastModified();

        Assert.assertTrue("didnt download 2nd time", firstSimDownload == lastModified);

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenServerOnline_whenPhetContentScraped_thenShouldThrowIllegalArgumentJarNotSupported() throws IOException {
        File tmpDir = Files.createTempDirectory("testphetcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        PhetContentScraper scraper = new PhetContentScraper(mockWebServer.url("/legacy/jar").toString(), tmpDir);
        scraper.scrapContent();

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenServerOnline_whenPhetContentScraped_thenShouldThrowIllegalArgumentFlashNotSupported() throws IOException {
        File tmpDir = Files.createTempDirectory("testphetcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        PhetContentScraper scraper = new PhetContentScraper(mockWebServer.url("/legacy/flash").toString(), tmpDir);
        scraper.scrapContent();

    }

    @Test
    public void givenServerOnline_whenUrlFound_findAllSimulations() throws IOException {

        IndexPhetContentScraper index = new IndexPhetContentScraper();
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        File tmpDir = Files.createTempDirectory("testphetindexscraper").toFile();

        index.findContent(mockWebServer.url(PHET_MAIN_CONTENT).toString(), tmpDir);

    }


    @Test
    public void givenDirectoryOfTranslationsIsCreated_findAllTranslationRelations() throws IOException {


        File tmpDir = Files.createTempDirectory("testphetcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        PhetContentScraper scraper = new PhetContentScraper(mockWebServer.url("/api/simulation/equality-explorer-two-variables").toString(), tmpDir);
        scraper.scrapContent();


        ArrayList<OpdsEntryWithRelations> translationList = scraper.getTranslations(tmpDir);

        Assert.assertTrue(translationList.get(0).getLanguage().equals("es"));

    }



    @Test
    public void testCommand() throws IOException{

        if(System.getProperty("phetUrl") != null && System.getProperty("phetDir") != null){
            File tmp = new File(System.getProperty("phetDir"));
            PhetContentScraper scraper = new PhetContentScraper(System.getProperty("phetUrl"),tmp);
            scraper.scrapContent();
            AssertAllFiles(tmp, scraper);
        }
    }

    @Test
    public void testIndexCommand() throws IOException{
        IndexPhetContentScraper content = new IndexPhetContentScraper();
        if(System.getProperty("findPhetUrl") != null && System.getProperty("findPhetDir") != null) {
            content.findContent(System.getProperty("findPhetUrl"), new File(System.getProperty("findPhetDir")));
        }
    }





}