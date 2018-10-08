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
import static org.springframework.cloud.kubernetes.config.ConfigMapTestUtil.readResourceFile;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.restassured.RestAssured;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
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
	"spring.application.name=" + ConfigMapsMixedSpringBootTest.APPLICATION_NAME,
	"spring.cloud.kubernetes.config.enableApi=true",
	"spring.cloud.kubernetes.config.paths="
		+ ConfigMapsMixedSpringBootTest.FILE_NAME_FULL_PATH
})
public class ConfigMapsMixedSpringBootTest {

	protected static final String FILES_ROOT_PATH = "/tmp/scktests";
	protected static final String FILE_NAME = "application-path.yaml";
	protected static final String FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + FILE_NAME;

	protected static final String APPLICATION_NAME = "configmap-mixed-example";

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
		ConfigMapTestUtil.createFileWithContent(FILE_NAME_FULL_PATH, readResourceFile("application-path.yaml"));

		HashMap<String, String> data = new HashMap<>();
		data.put("bean.morning", "Buenos Dias ConfigMap, %s");
		server.expect().withPath("/api/v1/namespaces/test/configmaps/" + APPLICATION_NAME)
			.andReturn(200, new ConfigMapBuilder().withNewMetadata()
				.withName(APPLICATION_NAME).endMetadata().addToData(data).build())
			.always();
	}

	@AfterClass
	public static void teardownAfterClass() {
		newArrayList(
			FILE_NAME_FULL_PATH,
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
	public void greetingInputShouldReturnPropertyFromFile() {
		when().get("/greeting").then().statusCode(200).body("content",
			is("Hello ConfigMap, World from path"));
	}

	@Test
	public void farewellInputShouldReturnPropertyFromFile() {
		when().get("/farewell").then().statusCode(200).body("content",
			is("Bye ConfigMap, World from path"));
	}

	@Test
	public void morningInputShouldReturnPropertyFromApi() {
		when().get("/morning").then().statusCode(200).body("content",
			is("Buenos Dias ConfigMap, World"));
	}

}
