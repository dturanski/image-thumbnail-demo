/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.example.image.thumbnail.standalone;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.spring.example.image.thumbnail.processor.ThumbnailProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.fn.http.request.HttpRequestFunctionConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

@SpringBootApplication
@Import(HttpRequestFunctionConfiguration.class)
public class ThumbnailStandaloneApplication {
	private static Logger logger = LoggerFactory.getLogger(ThumbnailStandaloneApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ThumbnailStandaloneApplication.class, args);
	}

	@Bean
	public CommandLineRunner runDemo(HttpRequestFunctionConfiguration.HttpRequestFunction httpRequestFunction,
			ConfigurableApplicationContext context) {
		return args -> {
			String[] imageUrls = new String[] {
					"https://i.imgur.com/FQtKSuv.jpeg",
					"https://i.imgur.com/4Cndaul.jpeg",
					"https://i.imgur.com/FCPLS42.jpeg",
					"https://i.imgur.com/DhzHsz8.jpg",
					"https://i.imgur.com/G7t1ZZl.jpg"
			};
			ThumbnailProcessor thumbnailProcessor = new ThumbnailProcessor();
			CountDownLatch countDownLatch = new CountDownLatch(imageUrls.length);
			AtomicInteger index = new AtomicInteger();

			Flux<Message<?>> urls = Flux.fromArray(imageUrls)
					.map(GenericMessage::new);

			httpRequestFunction.apply(urls)
					.subscribe(image -> {
						byte[] bytes = thumbnailProcessor.apply((byte[]) image);
						File thumbnail = new File(String.format("thumbnail-%d.jpg", index.incrementAndGet()));
						logger.info("creating thumbnail {}", thumbnail.getAbsolutePath());
						try (FileOutputStream fos = new FileOutputStream(thumbnail)) {
							fos.write(bytes);
						}
						catch (IOException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
						finally {
							countDownLatch.countDown();
						}
					});
			countDownLatch.await(10, TimeUnit.SECONDS);
			context.close();
		};
	}
}
