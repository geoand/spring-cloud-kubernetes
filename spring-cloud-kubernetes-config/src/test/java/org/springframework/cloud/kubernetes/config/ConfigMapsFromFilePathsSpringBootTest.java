/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import static io.restassured.RestAssured.when;
import static org.assertj.core.util.Lists.newArrayList;
import static org.hamcrest.core.Is.is;
import static org.springframework.cloud.kubernetes.config.ConfigMapTestUtil.createFileWithContent;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.restassured.RestAssured;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class , properties = {
	"spring.application.name=configmap-path-example",
	"spring.cloud.kubernetes.config.enableApi=false",
	"spring.cloud.kubernetes.config.paths="
		+ ConfigMapsFromFilePathsSpringBootTest.FIRST_FILE_NAME_FULL_PATH + ","
		+ ConfigMapsFromFilePathsSpringBootTest.SECOND_FILE_NAME_FULL_PATH
})
public class ConfigMapsFromFilePathsSpringBootTest {

	protected static final String FILES_ROOT_PATH = "/tmp/scktests";
	protected static final String FIRST_FILE_NAME = "application.properties";
	protected static final String SECOND_FILE_NAME = "extra.properties";
	protected static final String UNUSED_FILE_NAME = "unused.properties";
	protected static final String FIRST_FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + FIRST_FILE_NAME;
	protected static final String SECOND_FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + SECOND_FILE_NAME;
	protected static final String UNUSED_FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + UNUSED_FILE_NAME;

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Value("${local.server.port}")
	private int port;

	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
				"false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");

		Files.createDirectories(Paths.get(FILES_ROOT_PATH));
		createFileWithContent(FIRST_FILE_NAME_FULL_PATH, "bean.greeting=Hello from path!");
		createFileWithContent(SECOND_FILE_NAME_FULL_PATH, "bean.farewell=Bye from path!");
		createFileWithContent(UNUSED_FILE_NAME_FULL_PATH, "bean.morning=Morning from path!");
	}

	@AfterClass
	public static void teardownAfterClass() {
		newArrayList(
			FIRST_FILE_NAME_FULL_PATH,
			SECOND_FILE_NAME_FULL_PATH,
			SECOND_FILE_NAME_FULL_PATH,
			FILES_ROOT_PATH
		).forEach(fn -> {
			try {
				Files.delete(Paths.get(fn));
			} catch (IOException ignored) {}
		});
	}

	@Before
	public void setUp() {
		RestAssured.baseURI = String.format("http://localhost:%d/api", port);
	}

	@Test
	public void greetingInputShouldReturnPropertyFromFirstFile() {
		when().get("/greeting").then().statusCode(200).body("content",
			is("Hello from path!"));
	}

	@Test
	public void farewellInputShouldReturnPropertyFromSecondFile() {
		when().get("/farewell").then().statusCode(200).body("content",
			is("Bye from path!"));
	}

	@Test
	public void morningInputShouldReturnDefaultValue() {
		when().get("/morning").then().statusCode(200).body("content",
			is("Good morning, World!"));
	}

}
