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

package io.spring.example.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.fn.http.request.HttpRequestFunctionConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.fn.http.request.HttpRequestFunctionConfiguration.HttpRequestFunction;

@SpringBootApplication
@Import(HttpRequestFunctionConfiguration.class)
public class ImageThumbnailDemoApplication {
	private static Logger logger = LoggerFactory.getLogger(ImageThumbnailDemoApplication.class);

	public static void main(String[] args) {

		SpringApplication.run(ImageThumbnailDemoApplication.class,
				"--http.request.url-expression=payload",
				"--http.request.expected-response-type=byte[]");
	}

	@Configuration
	static class WebClientConfiguration {
		@Bean
		public WebClient myWebClient() {
			return WebClient.builder()
					.codecs(clientCodecConfigurer -> {
						clientCodecConfigurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024);
					})
					.build();
		}
	}

	@Bean
	public CommandLineRunner runDemo(HttpRequestFunction httpRequestFunction, ConfigurableApplicationContext context) {
		return args -> {

			String[] imageUrls = new String[] {
					"https://i.imgur.com/FQtKSuv.jpeg",
					"https://i.imgur.com/4Cndaul.jpeg",
					"https://i.imgur.com/FCPLS42.jpeg",
					"https://i.imgur.com/DhzHsz8.jpg",
					"https://i.imgur.com/G7t1ZZl.jpg"
			};
			CountDownLatch countDownLatch = new CountDownLatch(imageUrls.length);
			ImageScaler imageScaler = new ImageScaler();
			AtomicInteger index = new AtomicInteger();

			Flux<Message<?>> urls = Flux.fromArray(imageUrls)
					.map(GenericMessage::new);

			httpRequestFunction.apply(urls).subscribe(image -> {
				ByteArrayInputStream bis = new ByteArrayInputStream((byte[]) image);
				File thumbnail = new File(String.format("thumbnail-%d.jpg", index.incrementAndGet()));
				logger.info("creating thumbnail {}", thumbnail.getAbsolutePath());
				try {
					imageScaler.scale(bis, 10, 10, thumbnail, "JPEG");
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

	static class ImageScaler {

		public void scale(InputStream inputStream, int x_sample, int y_sample, File destination, String formatName)
				throws IOException {

			ImageInputStream iis = ImageIO.createImageInputStream(inputStream);

			Iterator iter = ImageIO.getImageReaders(iis);
			if (!iter.hasNext()) {
				return;
			}

			ImageReader reader = (ImageReader) iter.next();

			ImageReadParam params = reader.getDefaultReadParam();

			reader.setInput(iis, true, true);

			params.setSourceSubsampling(x_sample, y_sample, 0, 0);

			BufferedImage img = reader.read(0, params);

			ImageIO.write(img, formatName, destination);

			inputStream.close();
		}
	}
}
