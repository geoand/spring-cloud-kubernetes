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
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.restassured.RestAssured;
import java.util.Base64;
import java.util.HashMap;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class, properties = {
		"spring.application.name=" + SecretsSpringBootTest.APPLICATION_NAME,
		"spring.cloud.kubernetes.secrets.enableApi=true",
		"spring.cloud.kubernetes.reload.enabled=false" })
public class SecretsSpringBootTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired(required = false)
	Config config;

	protected static final String APPLICATION_NAME = "secrets-example";

	@Value("${local.server.port}")
	private int port;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
				"false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");

		HashMap<String, String> data = new HashMap<String, String>() {{
			put("bean.greeting", encodeValue("Hello Secret, %s!"));
			put("bean.farewell", encodeValue("Bye Secret, %s!"));
		}};
		server.expect().withPath("/api/v1/namespaces/test/secrets/" + APPLICATION_NAME)
				.andReturn(200, new SecretBuilder().withNewMetadata()
						.withName(APPLICATION_NAME).endMetadata().addToData(data).build())
				.always();
	}

	private static String encodeValue(String s) {
		return Base64.getEncoder().encodeToString(s.getBytes());
	}

	@Before
	public void setUp() {
		RestAssured.baseURI = String.format("http://localhost:%d/api", port);
	}

	@Test
	public void testGreetingEndpoint() {
		when().get("greeting").then().statusCode(200).body("content",
				is("Hello Secret, World!"));
	}

	@Test
	public void testFarewellEndpoint() {
		when().get("farewell").then().statusCode(200).body("content",
			is("Bye Secret, World!"));
	}
}
