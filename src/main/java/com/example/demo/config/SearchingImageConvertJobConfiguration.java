package com.example.demo.config;

import com.example.demo.jobs.domain.Keyword;
import com.example.demo.jobs.domain.KeywordRepository;
import com.example.demo.jobs.domain.KeywordUrl;
import com.example.demo.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableBatchProcessing
public class SearchingImageConvertJobConfiguration {

    private static final String SEARCH_NAVER_URL = "https://search.naver.com/search.naver?where=news&query=";
    private static final String NEWS_QUERY = "&sm=tab_srt&sort=1";

    private static final String BASE_S3_PATH = "https://s3.us-east-2.amazonaws.com/demosearchingimage/";

    public static final String JOB_NAME = "SearchingImageConvertBatch";
    public static final String BEAN_PREFIX = JOB_NAME + "_";

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final EntityManagerFactory emf;

    private final S3Uploader s3Uploader;

    private final KeywordRepository keywordRepository;

    @Value("${chunkSize:1000}")
    private int chunkSize;

    @Bean(JOB_NAME)
    public Job job() {
        return jobBuilderFactory.get(JOB_NAME)
                .preventRestart()
                .start(step())
                .build();
    }

    @Bean(BEAN_PREFIX + "step")
    @JobScope
    public Step step() {
        return stepBuilderFactory.get(BEAN_PREFIX + "step")
                .<Keyword, KeywordUrl>chunk(chunkSize)
                .reader(reader())
                .processor(processor())
                .writer(jpaItemWriter())
                .build();
    }

    @Bean
    public JpaPagingItemReader<Keyword> reader() {
        return new JpaPagingItemReaderBuilder<Keyword>()
                .name(BEAN_PREFIX + "reader")
                .entityManagerFactory(emf)
                .pageSize(chunkSize)
                .queryString("SELECT k FROM Keyword k")
                .build();
    }

    @Bean
    public ItemProcessor<Keyword, KeywordUrl> processor() {
        return keyword -> {
            // 크롬 드라이버의 경로를 설정
            System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
//            System.setProperty("webdriver.chrome.driver", System.getProperty("user.dir") + "/drivers/chromedriver");

            String url = keyword.getName() + "/" + keyword.getName() + System.currentTimeMillis();
            log.info("Keyword url = {}", url);

            // 해당 검색어로 크롤링 후 스크린샷으로 변환
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--start-maximized");
            options.addArguments("--start-fullscreen");
            options.addArguments("headless");
            options.addArguments("--lang=ko");

            WebDriver driver = new ChromeDriver(options);
            driver.get(SEARCH_NAVER_URL + keyword.getName() + NEWS_QUERY);

            WebElement body = driver.findElement(By.tagName("body"));
            byte[] imageByteTest = ((TakesScreenshot)body).getScreenshotAs(OutputType.BYTES);
//            try (FileOutputStream fos = new FileOutputStream(System.getProperty("user.home") + "/screenshot/" + keyword.getName() + System.currentTimeMillis() + ".png")) {
//                fos.write(imageByteTest);
//                fos.close();
//            }
//
//            // 스크린샷
//            TakesScreenshot screenshot = (TakesScreenshot) driver;
//            byte[] imageByte = screenshot.getScreenshotAs(OutputType.BYTES);


            // 드라이버 종료
            driver.quit();

            s3Uploader.uploadFile(url, imageByteTest);
            String s3Url = BASE_S3_PATH + url;
            KeywordUrl keywordUrl = new KeywordUrl(keyword.getId(), s3Url, LocalDateTime.now());

            // 최근 이미지 생성 시간으로 modified_date를 변경해줌
            keywordRepository.updateKeywordSetModifiedDateForName(LocalDateTime.now(), keyword.getName());
            keywordRepository.updateKeywordSetImgCreatedDateForName(LocalDateTime.now(), keyword.getName());

            return keywordUrl;
        };
    }

    private ItemWriter<KeywordUrl> writer() {
        return items -> {
            for (KeywordUrl item : items) {
                log.info("KeywordUrl = {}", item);
            }
        };
    }

    @Bean
    public JpaItemWriter<KeywordUrl> jpaItemWriter() {
        JpaItemWriter<KeywordUrl> jpaItemWriter = new JpaItemWriter<>();
        jpaItemWriter.setEntityManagerFactory(emf);
        return jpaItemWriter;
    }
}