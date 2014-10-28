/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package ddf.catalog.test;

import com.jayway.restassured.http.ContentType;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

/**
* Tests Federation aspects.
*
* @author Ashraf Barakat
* @author Phillip Klinefelter
* @author ddf.isgs@lmco.com
*
*/
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class TestFederation extends TestCatalog {

    private static XLogger LOGGER = new XLogger(LoggerFactory.getLogger(TestFederation.class));

    private static final String SAMPLE_DATA = "sample data";

    private static final int XML_RECORD_INDEX = 1;

    private static final int GEOJSON_RECORD_INDEX = 0;

    private static final String CSW_PATH = SERVICE_ROOT + "/csw";

    private static final String DEFAULT_KEYWORD = "text";

    private static final String RECORD_TITLE_1 = "myTitle";

    private static final String RECORD_TITLE_2 = "myXmlTitle";

    private static final String OPENSEARCH_SOURCE_ID = "openSearchSource";

    private static final String CSW_SOURCE_ID = "cswSource";

    /*
     * The fields must be static if they are purposely used across all test methods.
     */
    private static boolean ranBefore = false;

    private static String[] metacardIds = new String[2];

    /**
     * Runs each time before each test, items that don't need to be run each time have a conditional
     * flag.
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Before
    public void beforeFederation() {
        if (!ranBefore) {
            try {
                LOGGER.info("Running one-time federation setup.");

                OpenSearchSourceProperties openSearchProperties = new OpenSearchSourceProperties();
                createManagedService(OpenSearchSourceProperties.FACTORY_PID,
                        openSearchProperties.createDefaultProperties(OPENSEARCH_SOURCE_ID));

                waitForCxfService("/csw");
                get(CSW_PATH + "?_wadl").prettyPrint();
                CswSourceProperties cswProperties = new CswSourceProperties();
                createManagedService(CswSourceProperties.FACTORY_PID,
                        cswProperties.createDefaultProperties(CSW_SOURCE_ID));

                waitForFederatedSource(OPENSEARCH_SOURCE_ID);
                waitForFederatedSource(CSW_SOURCE_ID);

                File file = new File("sample.txt");
                file.createNewFile();
                FileUtils.write(file, SAMPLE_DATA);
                String fileLocation = file.toURI().toURL().toString();
                metacardIds[GEOJSON_RECORD_INDEX] = ingest(Library.getSimpleGeoJson(),
                        "application/json");

                LOGGER.debug("File Location: {}", fileLocation);
                metacardIds[XML_RECORD_INDEX] = ingest(Library.getSimpleXml(fileLocation), "text/xml");

                LOGGER.info("Source status: \n{}", get(REST_PATH + "sources").body());

                ranBefore = true;
            } catch (Exception e) {
                LOGGER.error("Failed to setup federation.", e);
                fail("Failed to setup federation: " + e.getMessage());
            }
        }
    }

    /**
     * Given what was ingested in beforeTest(), tests that a Federated wildcard search will return
     * all appropriate record(s).
     *
     * @throws Exception
     */
    @Test
    public void testFederatedQueryByWildCardSearchPhrase() throws Exception {
        String queryUrl = OPENSEARCH_PATH + "?q=*&format=xml&src=" + OPENSEARCH_SOURCE_ID;

        when().get(queryUrl).then().log().all().assertThat().body(containsString(RECORD_TITLE_1),
                containsString(RECORD_TITLE_2));
    }

    /**
     * Given what was ingested in beforeTest(), tests that a Federated search phrase will return the
     * appropriate record(s).
     *
     * @throws Exception
     */
    @Test
    public void testFederatedQueryBySearchPhrase() throws Exception {
        String queryUrl = OPENSEARCH_PATH + "?q=" + DEFAULT_KEYWORD + "&format=xml&src="
                + OPENSEARCH_SOURCE_ID;

        when().get(queryUrl).then().log().all().assertThat().body(containsString(RECORD_TITLE_1),
                containsString(RECORD_TITLE_2));
    }

    /**
     * Tests that given a bad test phrase, no records should have been returned.
     *
     * @throws Exception
     */
    @Test
    public void testFederatedQueryByNegativeSearchPhrase() throws Exception {
        String negativeSearchPhrase = "negative";
        String queryUrl = OPENSEARCH_PATH + "?q=" + negativeSearchPhrase + "&format=xml&src="
                + OPENSEARCH_SOURCE_ID;

        when().get(queryUrl).then().log().all().assertThat().body(
                not(containsString(RECORD_TITLE_1)), not(containsString(RECORD_TITLE_2)));
    }

    /**
     * Tests that a federated search by ID will return the right record.
     *
     * @throws Exception
     */
    @Test
    public void testFederatedQueryById() throws Exception {
        String restUrl = REST_PATH + "sources/" + OPENSEARCH_SOURCE_ID + "/"
                + metacardIds[GEOJSON_RECORD_INDEX];

        when().get(restUrl).then().log().all().assertThat().body(containsString(RECORD_TITLE_1),
                not(containsString(RECORD_TITLE_2)));
    }

    /**
     * Tests Source can retrieve product existing product.
     *
     * @throws Exception
     */
    @Test
    public void testFederatedRetrieveExistingProduct() throws Exception {
        String restUrl = REST_PATH + "sources/" + OPENSEARCH_SOURCE_ID + "/"
                + metacardIds[XML_RECORD_INDEX] + "?transform=resource";

        when().get(restUrl).then().log().all().assertThat().contentType("text/plain").body(is(
                SAMPLE_DATA));
    }

    /**
     * Tests Source can retrieve nonexistent product.
     *
     * @throws Exception
     */
    @Test
    public void testFederatedRetrieveNoProduct() throws Exception {
        String restUrl = REST_PATH + "sources/" + OPENSEARCH_SOURCE_ID + "/"
                + metacardIds[GEOJSON_RECORD_INDEX] + "?transform=resource";

        expect().log().all().body(containsString("Unknown resource request")).when().get(restUrl);
    }

    @Test
    public void testFederatedCwqQueryByWildCardSearchPhrase() throws Exception {
        String wildcardQuery = Library.getCswQuery("AnyText", "*");

        given().contentType(ContentType.XML).body(wildcardQuery).when().post(CSW_PATH)
                .then().log().all().assertThat().body(
                hasXPath("/GetRecordsResponse/SearchResults/Record/identifier[text()='" +
                        metacardIds[GEOJSON_RECORD_INDEX] + "']"),
                hasXPath("/GetRecordsResponse/SearchResults/Record/identifier[text()='" +
                        metacardIds[XML_RECORD_INDEX] + "']"),
                hasXPath("/GetRecordsResponse/SearchResults/@numberOfRecordsReturned",
                        is("2")));
    }

    @Test
    public void testFederatedCwqQueryByTitle() throws Exception {
        String titleQuery = Library.getCswQuery("title", "myTitle");

        given().contentType(ContentType.XML).body(titleQuery).when().post(CSW_PATH)
                .then().log().all().assertThat().body(
                hasXPath("/GetRecordsResponse/SearchResults/Record/identifier",
                        is(metacardIds[GEOJSON_RECORD_INDEX])),
                hasXPath("/GetRecordsResponse/SearchResults/@numberOfRecordsReturned",
                        is("1")));
    }

    public class OpenSearchSourceProperties extends Hashtable<String, Object> {

        public static final String SYMBOLIC_NAME = "catalog-opensearch-source";

        public static final String FACTORY_PID = "OpenSearchSource";

        public Dictionary<String, Object> createDefaultProperties(String sourceId) {
            this.putAll(getMetatypeDefaults(SYMBOLIC_NAME, FACTORY_PID));

            this.put("shortname", sourceId);
            this.put("endpointUrl", OPENSEARCH_PATH);

            return this;
        }
    }

    public class CswSourceProperties extends Hashtable<String, Object> {

        public static final String SYMBOLIC_NAME = "spatial-csw-source";

        public static final String FACTORY_PID = "Csw_Federated_Source";

        public Dictionary<String, Object> createDefaultProperties(String sourceId) {
            this.putAll(getMetatypeDefaults(SYMBOLIC_NAME, FACTORY_PID));

            this.put("id", sourceId);
            this.put("cswUrl", CSW_PATH);

            return this;
        }
    }

}