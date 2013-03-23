/*
 * RHQ Management Platform
 * Copyright (C) 2005-2013 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package org.rhq.modules.integrationTests.restApi;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;

import org.junit.Test;

import org.rhq.modules.integrationTests.restApi.d.CreateCBRRequest;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.isOneOf;

/**
 * Test content upload and creation of content based resources
 * @author Heiko W. Rupp
 */
public class ContentTest extends AbstractBase {

    @Test
    public void testUpload() throws Exception {

        InputStream in =
            getClass().getClassLoader().getResourceAsStream("test-simple.war");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int data;
        while ((data = in.read())!=-1) {
            baos.write(data);
        }

        byte[] bytes = baos.toByteArray();

        given()
            .auth().preemptive().basic("rhqadmin", "rhqadmin")
            .body(bytes)
            .contentType(ContentType.BINARY)
            .header(acceptJson)
            .log().everything()
        .expect()
            .statusCode(isOneOf(200, 201))
            .body("value", startsWith("rhq-rest-"))
            .body("value",endsWith(".bin"))
            .log().ifError()
        .when()
            .post("/content/fresh");


    }
    @Test
    public void testUploadAndDelete() throws Exception {

        InputStream in =
            getClass().getClassLoader().getResourceAsStream("test-simple.war");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int data;
        while ((data = in.read())!=-1) {
            baos.write(data);
        }

        byte[] bytes = baos.toByteArray();
        int size = bytes.length;

        String handle =
        given()
            .auth().preemptive().basic("rhqadmin", "rhqadmin")
            .body(bytes)
            .contentType(ContentType.BINARY)
            .header(acceptJson)
        .expect()
            .body("value", startsWith("rhq-rest-"))
            .body("value", endsWith(".bin"))
            .statusCode(isOneOf(200, 201))
        .when()
            .post("/content/fresh")
        .jsonPath()
            .getString("value");

        Integer uploadedSize =
        given()
            .pathParam("handle", handle)
            .header(acceptJson)
        .expect()
            .statusCode(200)
        .when()
            .get("/content/{handle}/info")
        .jsonPath().getInt("value");

        assert uploadedSize!=null;
        assert uploadedSize==size;

        given()
            .pathParam("handle",handle)
            .header(acceptJson)
        .expect()
            .statusCode(204)
            .log().ifError()
        .when()
            .delete("/content/{handle}");

    }

    @Test
    public void testDeleteUnknownContent() throws Exception {

        given()
            .pathParam("handle","Frobnitz")
            .header(acceptJson)
        .expect()
            .statusCode(204)
            .log().ifError()
        .when()
            .delete("/content/{handle}");

    }

    @Test
    public void testCreatePackageBasedResource() throws Exception {

        InputStream in =
            getClass().getClassLoader().getResourceAsStream("test-simple.war");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int data;
        while ((data = in.read())!=-1) {
            baos.write(data);
        }

        byte[] bytes = baos.toByteArray();

        // Upload content
        String handle =
        given()
            .auth().preemptive().basic("rhqadmin", "rhqadmin")
            .body(bytes)
            .contentType(ContentType.BINARY)
            .header(acceptJson)
        .expect()
            .body("value",startsWith("rhq-rest-"))
            .body("value",endsWith(".bin"))
            .statusCode(isOneOf(200, 201))
        .when()
            .post("/content/fresh")
        .jsonPath()
            .getString("value");

        // Find an EAP 6 server
        List<Map<String,Object>> resources =
        given()
            .header(acceptJson)
            .queryParam("q","EAP (127.0.0.1:9990)")  // TODO fragile -- better search for it?
            .queryParam("category","SERVER")
        .expect()
            .statusCode(200)
            .log().ifError()
        .when()
            .get("/resource")
        .jsonPath().getList("$");

        assert resources.size()>0;

        int as7Id = Integer.valueOf((String)resources.get(0).get("resourceId"));

        // create child of eap6 as deployment
        try {
            CreateCBRRequest resource = new CreateCBRRequest();
            resource.setParentId(as7Id);
            resource.setResourceName("test-simple.war");


            // type of the new resource
            resource.setTypeName("Deployment");
            resource.setPluginName("JBossAS7");

            // set plugin config (path) and deploy config (runtime-name)
            resource.getPluginConfig().put("path","deployment");
            resource.getResourceConfig().put("runtimeName","test-simple.war");

            Response response =
            given()
                .body(resource) // Type of new resource
                .queryParam("handle", handle)
                .contentType(ContentType.JSON)
                .header(acceptJson)
                .log().everything()
            .expect()
                .statusCode(isOneOf(200, 201, 302))
                .log().everything()
            .when()
                .post("/resource");

            System.out.println("after post");
            System.out.flush();

            int status = response.getStatusCode();
            String location = response.getHeader("Location");

            System.out.println("Location " + location + "\n\n");
            assert location!=null;

            // We need to check what we got. A 302 means the deploy is still
            // in progress, so we need to wait a little longer
            while (status==302) {

                status =
                given()
                    .header(acceptJson)
                    .log().everything()
                .expect()
                    .statusCode(isOneOf(200,201,302))
                    .log().everything()
                .when()
                    .get(location)
                .getStatusCode();
            }

        } finally {

            // Remove the uploaded content
            given()
                .pathParam("handle",handle)
                .header(acceptJson)
            .expect()
                .statusCode(204)
                .log().ifError()
            .when()
                .delete("/content/{handle}");


            // try to remove the created resource
            Response response =
            given()
                .queryParam("q", "test-simple.war")
                .header(acceptJson)
            .expect()
                .log().everything()
            .when()
                .get("/resource");

            List links = response.body().jsonPath().getList("links");

            System.out.println(links);
            assert links!=null;

            if (links.size()>0) {

                String link = null;
                @SuppressWarnings("unchecked")
                List<Map<String,Map<String,String>>> listOfMaps = (List<Map<String, Map<String, String>>>) links.get(0);

                for (Map<String,Map<String,String>>  map : listOfMaps) {
                    if (map.containsKey("self")) {
                        link = map.get("self").get("href");
                        break;
                    }
                }

                assert link != null;

                System.out.println("Link: " + link);

                given()
                    .header(acceptJson)
                    .queryParam("physical","true") // Also remove target on the EAP instance
                .expect()
                    .log().everything()
                .when()
                    .delete(link);
            }

        }

    }

    @Test
    public void testCreateCBRBadHandle() throws Exception {

        CreateCBRRequest resource = new CreateCBRRequest();
        resource.setParentId(123);
        resource.setResourceName("test-simple.war");


        // type of the new resource
        resource.setTypeName("Deployment");
        resource.setPluginName("JBossAS7");

        // set plugin config (path) and deploy config (runtime-name)
        resource.getPluginConfig().put("path","deployment");
        resource.getResourceConfig().put("runtimeName","test-simple.war");

        Response response =
        given()
            .body(resource) // Type of new resource
            .queryParam("handle", "This is a joke")
            .contentType(ContentType.JSON)
            .header(acceptJson)
            .log().everything()
        .expect()
                .statusCode(404)
            .log().everything()
        .when()
            .post("/resource");

    }
}
